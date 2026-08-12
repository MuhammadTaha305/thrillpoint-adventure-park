# ThrillPoint Adventure Park

A desktop ticketing and maintenance management system for an adventure park, written in Java with a JavaFX interface.

The park has two sides that want opposite things. Ticketing wants every ride open, because a closed ride earns nothing. Maintenance wants rides closed, because a worn ride is dangerous. Both act on the same ride objects at the same time, from different threads, and keeping that safe is the heart of the project.

---

## Requirements

- **JDK 17 or newer**
- **JavaFX**, which is no longer bundled with the JDK

Pick whichever option matches your machine.

### Option A — a JDK that already includes JavaFX (simplest)

Install Liberica Full JDK 17+ or Azul Zulu FX 17+. JavaFX is then on the classpath and nothing else is needed.

### Option B — a normal JDK plus the OpenJFX SDK

Download the SDK from [openjfx.io](https://openjfx.io) or [gluonhq.com/products/javafx](https://gluonhq.com/products/javafx), matching your platform (on Apple Silicon choose the `aarch64` build). Then either:

- unzip it into this folder as `javafx-sdk`, or
- set `JAVAFX_HOME` to its `lib` directory

## Build and run

```bash
./run.sh            # macOS / Linux
run.bat             # Windows
```

The script detects which of the two setups is present and adds the module path only when it is needed.

```bash
./run.sh build      # compile only
./run.sh demo       # race condition demonstration, console only, no JavaFX required
```

To compile by hand instead:

```bash
mkdir -p bin
javac -d bin src/*.java
java -cp bin Main
```

Add `--module-path $JAVAFX_HOME --add-modules javafx.controls` to both commands if you are using Option B.

---

## What the application does

- Sells four ticket types: **Single Ride**, **Day Pass**, **VIP Fast Pass** and **Group**. Each behaves differently when a visitor tries to board.
- Prices follow the time of day. Four pricing periods rotate as the simulated clock advances.
- Day Pass and VIP holders can re-board any ride on the ticket they already hold, at no charge.
- Group tickets price the buyer plus every named member, and check each member's height against the ride separately.
- Rides run on their own threads, load visitors from a queue and gain wear on every cycle. At 60% wear a ride goes offline automatically and a maintenance task is raised.
- Technicians have certification levels. Only a Senior may repair a roller coaster.
- Management can force an inspection, close a ride, reopen it, and cancel a booking.
- Everything is saved to CSV files and a serialized snapshot, and restored on the next start.

---

## Threads

| Count | Thread | Job |
|---|---|---|
| 1 | `ParkClock` | Advances simulated time, rotates the pricing period, closes the park each day |
| 7 | `RideCycle` | One per ride: loads the queue, runs cycles, accumulates wear |
| 3 | `TicketCounter` | Simulates walk-up ticket sales happening at the same time |
| 2 | `MaintenanceCrew` | Claims tasks from the shared queue and assigns technicians |
| 1 | `AutoSaveService` | Writes the snapshot and the CSV files every 10 seconds |

That is 14 threads, plus a background thread inside `AuditLogger` that writes the log file so the simulation threads never wait on the disk.

There is also a pause switch and a 0.5x–8x speed control in the header, read by every thread through `SimulationControl`.

---

## How the shared data is protected

### Lock ordering

There are four kinds of monitor in the system. Every thread takes them in **this order and no other**:

```
Ride  ->  ParkState  ->  { Ticket, MaintenanceTask, Technician }
```

This is why `ParkState.raiseMaintenanceTask()`, `forceInspection()` and `completeTask()` are deliberately **not** declared `synchronized`. Each opens the *ride* monitor first, then `synchronized (this)`. Declaring them `synchronized` would take `ParkState` before `Ride`, the opposite of what `TicketingService.bookVisitor()` does, and two threads taking two locks in opposite orders deadlock.

### Short critical sections

No lock is held across a `Thread.sleep()`. A ride cycle is split into three parts:

1. `Ride.beginCycle()` claims the riders and sets the status to `RUNNING` — locked, microseconds
2. the four-second cycle — **no lock held**
3. `Ride.completeCycle()` records the results — locked, microseconds

`reopenIfRunning()` only reopens a ride that is still `RUNNING`, so an inspection raised during a cycle is not silently undone.

### Identifier generation

Ticket and task IDs come from `ParkState.nextTicketId()` and `nextTaskId()`, which are `synchronized` on `ParkState` and increment a counter. IDs are never derived from `list.size()` — three counter threads holding three different *ride* locks would all read the same size and produce the same ID.

### Consistent snapshots

`ParkState.saveTo()` is a `synchronized` **instance** method, so the object graph cannot be written to disk while another thread is halfway through changing a list.

---

## Object-oriented design

| Idea | Where it is used |
|---|---|
| Inheritance | `Person` → `Staff` → `Technician`; abstract `Ride` with 5 subclasses; abstract `Ticket` with 4 subclasses |
| Polymorphism | `Ticket.isValidForRide()` and `Ride.getRideType()` are answered differently by each subclass |
| Encapsulation | All fields private, setters validate before assigning, getters return copies of lists |
| Abstraction | Abstract `Ride` and `Ticket`; four interfaces with default and static methods |
| Strategy pattern | `PricingStrategy` interface with `StandardPricing`, `PeakHourPricing`, `StudentDiscountPricing`, `PublicHolidayPricing`, resolved by `PricingService` |
| Observer pattern | `AuditLogger.AuditLogListener`, implemented by `EventLogPanel` |
| Singleton | `AuditLogger` and `SimulationControl` |
| Static factory | `Ride.createByType()` rebuilds the correct subclass from a stored type token |
| Interface segregation | `Bookable` is ticketing's view of a ride, `Maintainable` is maintenance's view |

---

## Exception handling

Business rules are thrown, not returned as strings.

```
Exception
└── ParkException                     (checked - the caller can recover)
    ├── HeightRestrictionException    (+ actualHeight, requiredHeight)
    ├── RideClosedException
    ├── MaintenanceInProgressException
    ├── CapacityExceededException     (+ requested, available)
    ├── InvalidTicketException
    ├── TicketExpiredException        (+ ticketId)
    └── DataPersistenceException      (wraps IOException, keeps getCause())

RuntimeException
└── ParkRuntimeException              (unchecked - a broken invariant)
    ├── DuplicateEntityException
    └── EntityNotFoundException
```

`TicketingPanel` catches these specific-to-general and gives each one its own dialog, so a refusal always explains itself.

---

## File handling

| Mechanism | Files | Purpose |
|---|---|---|
| CSV text | `rides.csv`, `tickets.csv`, `visitors.csv` | Readable master data, editable outside the application |
| Serialization | `park_state.ser` | Full object graph including the ride queues |
| Plain text | `audit_log.txt`, `park_reports.txt` | Append-only event log and the exported report |

On startup the program tries the snapshot first, then the CSV files, then falls back to seven default rides, so it always starts on a clean machine.

`rides.csv` stores an explicit `RideType` column. The subclass is never guessed from the ride's name, because renaming a ride would then load it as the wrong type with the wrong capacity and height limit.

Corrupted rows are logged and skipped rather than aborting the load. All file access uses try-with-resources.

---

## Interface

Six tabs:

1. **Ticketing Counter** — book a visitor, with a live price preview that follows the current pricing period
2. **Rides Monitor** — live table with wear bars, uptime and load factor; cancel a booking, close or reopen a ride
3. **Visitors & Tickets** — search by name or ticket ID, and re-board a holder on their existing ticket
4. **Maintenance Operations** — task queue, technician roster with workload, safety log, force inspection
5. **Analytics Reports** — revenue and ridership charts, daily history, file export, race condition demo
6. **Live Log Feed** — colour-coded real-time event stream

Everything to do with the interface runs on the JavaFX application thread. The simulation threads never touch a control; a `Timeline` polls the model every two seconds instead, and log entries arrive through `Platform.runLater()`. `MainWindow` holds a list of `ParkPanel`, so it knows nothing about what any individual tab contains.

---

## Source layout

All 62 classes are in `src/`.

| Group | Types |
|---|---|
| People | `Person`, `Visitor`, `Staff`, `Technician` |
| Rides | `Ride`, `RollerCoaster`, `WaterSlide`, `ZipLine`, `ClimbingWall`, `FerrisWheel` |
| Tickets | `Ticket`, `SingleRideTicket`, `DayPassTicket`, `VIPFastPassTicket`, `GroupTicket`, `GroupMember` |
| Interfaces | `Identifiable`, `Bookable`, `Maintainable`, `PricingStrategy`, `ParkPanel` |
| Pricing | `PricingService`, `StandardPricing`, `PeakHourPricing`, `StudentDiscountPricing`, `PublicHolidayPricing` |
| Park state | `ParkState`, `Zone`, `MaintenanceTask`, `DayRecord` |
| Services | `TicketingService`, `CSVHandler`, `AuditLogger` |
| Threads | `ParkClock`, `RideCycle`, `TicketCounter`, `MaintenanceCrew`, `AutoSaveService`, `SimulationControl`, `RaceConditionDemo` |
| Exceptions | `ParkException` and 7 subclasses, `ParkRuntimeException` and 2 subclasses |
| Interface | `ParkApplication`, `MainWindow`, `Theme`, `Dialogs`, and the six tab classes |
| Entry point | `Main` |

Every public class and method carries a Javadoc comment explaining what it does and, where it matters, why it is written that way.

The class diagram is supplied separately as `UML_ClassDiagram.pdf`, and the design decisions are explained in `ProjectReport.pdf`.
