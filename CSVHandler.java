import java.io.BufferedReader;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

/**
 * CSV text persistence for rides, tickets and visitors (FR-12).
 *
 * <p>Uses only {@code FileReader}/{@code FileWriter}/{@code BufferedReader}/
 * {@code PrintWriter} in try-with-resources, as the course outline requires.
 * Malformed rows are logged and skipped rather than aborting the whole load.</p>
 *
 * <h2>Ride types are stored, not guessed</h2>
 * <p>{@code rides.csv} carries an explicit {@code RideType} column. The previous
 * version inferred the subclass from substrings of the ride's name
 * ({@code "coaster"}, {@code "drop"}, …), so renaming "Aqua Drop" to "Aqua Plunge"
 * silently reloaded it as a {@link FerrisWheel} with the wrong capacity, height
 * limit and wear rate. A legacy fallback still reads pre-{@code RideType} files
 * once, with a loud warning.</p>
 */
public class CSVHandler {

    /** Header written to {@code rides.csv}. */
    private static final String RIDES_HEADER =
        "Name,RideType,Zone,Capacity,MinHeight,WearRate,Wear,Status,RidersServed,Revenue";

    /** Header written to {@code tickets.csv}. */
    private static final String TICKETS_HEADER =
        "TicketID,VisitorName,BasePrice,CurrentPrice,TicketType,Expired,Details";

    /** Header written to {@code visitors.csv}. */
    private static final String VISITORS_HEADER = "Name,Age,Height,TicketID";

    private CSVHandler() {
        // static utility class, never instantiated
    }

    // ----------------------------------------------------------------- rides

    /**
     * Writes every ride, including its explicit type token.
     *
     * @param rides    the rides to write
     * @param filepath destination file
     * @throws DataPersistenceException when the write fails
     */
    public static void saveRides(List<Ride> rides, String filepath) throws DataPersistenceException {
        try (PrintWriter pw = new PrintWriter(new FileWriter(filepath))) {
            pw.println(RIDES_HEADER);
            for (Ride r : rides) {
                pw.println(escapeCSV(r.getName()) + ","
                           + escapeCSV(r.getRideType()) + ","
                           + escapeCSV(r.getZone()) + ","
                           + r.getCapacity() + ","
                           + r.getMinHeight() + ","
                           + r.getWearRate() + ","
                           + r.getWear() + ","
                           + r.getStatus() + ","
                           + r.getRidersServed() + ","
                           + r.getRevenue());
            }
        } catch (IOException e) {
            throw new DataPersistenceException(filepath, e);
        }
    }

    /**
     * {@link #saveRides(List, String)} that logs instead of throwing.
     *
     * @param rides    the rides to write
     * @param filepath destination file
     */
    public static void saveRidesQuietly(List<Ride> rides, String filepath) {
        try {
            saveRides(rides, filepath);
        } catch (DataPersistenceException e) {
            AuditLogger.getInstance().log("ERROR", "Failed to save rides CSV: " + e.getMessage());
        }
    }

    /**
     * Reads rides back, rebuilding the correct subclass from the stored type.
     *
     * @param filepath source file
     * @return the rides read; empty when the file does not exist
     */
    public static List<Ride> loadRides(String filepath) {
        List<Ride> rides = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new FileReader(filepath))) {

            String header = br.readLine();
            boolean hasRideType = header != null && header.toLowerCase().contains("ridetype");
            if (!hasRideType) {
                AuditLogger.getInstance().log("WARNING",
                    "rides.csv has no RideType column (legacy format). Falling back to name-based "
                    + "type detection for this load only - the file will be rewritten with an "
                    + "explicit RideType on the next auto-save.");
            }

            String line;
            while ((line = br.readLine()) != null) {
                if (line.trim().isEmpty()) {
                    continue;
                }
                try {
                    Ride ride = hasRideType ? parseModernRide(line) : parseLegacyRide(line);
                    if (ride != null) {
                        rides.add(ride);
                    }
                } catch (RuntimeException e) {
                    AuditLogger.getInstance().log("WARNING",
                        "Corrupted ride row skipped: " + line + ". Error: " + e.getMessage());
                }
            }
        } catch (IOException e) {
            // No file yet - expected on a first run, the caller seeds defaults.
        }
        return rides;
    }

    /**
     * Parses a row in the current format, with an explicit type column.
     *
     * @param line one CSV row
     * @return the reconstructed ride, or {@code null} when the row is too short
     */
    private static Ride parseModernRide(String line) {
        String[] t = splitCSV(line);
        if (t.length < 10) {
            return null;
        }
        Ride ride = Ride.createByType(t[1], t[0], t[2]);
        applyRideState(ride, t[3], t[4], t[5], t[6], t[7], t[8], t[9]);
        return ride;
    }

    /**
     * Parses a pre-{@code RideType} row by guessing from the name.
     *
     * <p>Kept only so existing data files still load once. Do not extend it.</p>
     *
     * @param line one legacy CSV row
     * @return the reconstructed ride, or {@code null} when the row is too short
     */
    private static Ride parseLegacyRide(String line) {
        String[] t = splitCSV(line);
        if (t.length < 9) {
            return null;
        }
        String name = t[0];
        String lower = name.toLowerCase();
        String type;
        if (lower.contains("coaster")) {
            type = Ride.TYPE_ROLLER_COASTER;
        } else if (lower.contains("slide") || lower.contains("drop")) {
            type = Ride.TYPE_WATER_SLIDE;
        } else if (lower.contains("zip")) {
            type = Ride.TYPE_ZIP_LINE;
        } else if (lower.contains("wall")) {
            type = Ride.TYPE_CLIMBING_WALL;
        } else {
            type = Ride.TYPE_FERRIS_WHEEL;
        }
        Ride ride = Ride.createByType(type, name, t[1]);
        applyRideState(ride, t[2], t[3], t[4], t[5], t[6], t[7], t[8]);
        return ride;
    }

    /**
     * Restores the mutable part of a ride from its CSV columns.
     *
     * @param ride         the ride to populate
     * @param capacity     riders per cycle
     * @param minHeight    safety minimum in metres
     * @param wearRate     wear added per cycle
     * @param wear         current wear
     * @param status       status ordinal
     * @param ridersServed lifetime riders
     * @param revenue      lifetime revenue
     */
    private static void applyRideState(Ride ride, String capacity, String minHeight, String wearRate,
                                       String wear, String status, String ridersServed, String revenue) {
        ride.setCapacity(Integer.parseInt(capacity));
        ride.setMinHeight(Double.parseDouble(minHeight));
        ride.setWearRate(Double.parseDouble(wearRate));
        ride.setWear(Double.parseDouble(wear));
        ride.setStatus(Integer.parseInt(status));
        ride.addRidersServed(Integer.parseInt(ridersServed));
        ride.addRevenue(Double.parseDouble(revenue));
    }

    // --------------------------------------------------------------- tickets

    /**
     * Writes every ticket sold.
     *
     * @param tickets  the tickets to write
     * @param filepath destination file
     * @throws DataPersistenceException when the write fails
     */
    public static void saveTickets(List<Ticket> tickets, String filepath) throws DataPersistenceException {
        try (PrintWriter pw = new PrintWriter(new FileWriter(filepath))) {
            pw.println(TICKETS_HEADER);
            for (Ticket t : tickets) {
                pw.println(escapeCSV(t.getTicketId()) + ","
                           + escapeCSV(t.getVisitorName()) + ","
                           + t.getBasePrice() + ","
                           + t.getCurrentPrice() + ","
                           + t.getTicketType() + ","
                           + t.isExpired() + ","
                           + escapeCSV(detailsOf(t)));
            }
        } catch (IOException e) {
            throw new DataPersistenceException(filepath, e);
        }
    }

    /**
     * {@link #saveTickets(List, String)} that logs instead of throwing.
     *
     * @param tickets  the tickets to write
     * @param filepath destination file
     */
    public static void saveTicketsQuietly(List<Ticket> tickets, String filepath) {
        try {
            saveTickets(tickets, filepath);
        } catch (DataPersistenceException e) {
            AuditLogger.getInstance().log("ERROR", "Failed to save tickets CSV: " + e.getMessage());
        }
    }

    /**
     * Renders the type-specific payload column for a ticket.
     *
     * @param t the ticket
     * @return the target ride for single-ride tickets, semicolon-joined member
     *         names for group tickets, otherwise the empty string
     */
    private static String detailsOf(Ticket t) {
        if (t instanceof SingleRideTicket) {
            return ((SingleRideTicket) t).getTargetRideName();
        }
        if (t instanceof GroupTicket) {
            List<GroupMember> members = ((GroupTicket) t).getMembers();
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < members.size(); i++) {
                sb.append(members.get(i).toStorageString());
                if (i < members.size() - 1) {
                    sb.append(";");
                }
            }
            return sb.toString();
        }
        return "";
    }

    /**
     * Reads tickets back, rebuilding the correct subclass from the stored type.
     *
     * @param filepath source file
     * @return the tickets read; empty when the file does not exist
     */
    public static List<Ticket> loadTickets(String filepath) {
        List<Ticket> tickets = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new FileReader(filepath))) {

            String header = br.readLine();
            boolean hasExpired = header != null && header.toLowerCase().contains("expired");

            String line;
            while ((line = br.readLine()) != null) {
                if (line.trim().isEmpty()) {
                    continue;
                }
                try {
                    String[] t = splitCSV(line);
                    if (t.length < 5) {
                        continue;
                    }
                    String ticketId = t[0];
                    String visitorName = t[1];
                    double basePrice = Double.parseDouble(t[2]);
                    double currentPrice = Double.parseDouble(t[3]);
                    int ticketType = Integer.parseInt(t[4]);

                    boolean expired = false;
                    String details;
                    if (hasExpired) {
                        expired = Boolean.parseBoolean(t.length > 5 ? t[5] : "false");
                        details = t.length > 6 ? t[6] : "";
                    } else {
                        details = t.length > 5 ? t[5] : "";
                    }

                    Ticket ticket = rebuildTicket(ticketId, visitorName, basePrice, ticketType, details);
                    if (ticket != null) {
                        ticket.setCurrentPrice(currentPrice);
                        if (expired) {
                            ticket.expire();
                        }
                        tickets.add(ticket);
                    }
                } catch (RuntimeException e) {
                    AuditLogger.getInstance().log("WARNING",
                        "Corrupted ticket row skipped: " + line + ". Error: " + e.getMessage());
                }
            }
        } catch (IOException e) {
            // No file yet - expected on a first run.
        }
        return tickets;
    }

    /**
     * Polymorphic factory for reloading a ticket.
     *
     * @param ticketId    the stored identifier
     * @param visitorName the purchaser
     * @param basePrice   the stored base price
     * @param ticketType  one of the {@code Ticket.TYPE_*} constants
     * @param details     the type-specific payload column
     * @return the rebuilt ticket, or {@code null} for an unknown type
     */
    private static Ticket rebuildTicket(String ticketId, String visitorName, double basePrice,
                                        int ticketType, String details) {
        switch (ticketType) {
            case Ticket.TYPE_SINGLE:
                return new SingleRideTicket(ticketId, visitorName, basePrice, details);
            case Ticket.TYPE_DAY_PASS:
                return new DayPassTicket(ticketId, visitorName, basePrice);
            case Ticket.TYPE_VIP:
                return new VIPFastPassTicket(ticketId, visitorName, basePrice);
            case Ticket.TYPE_GROUP:
                // Entries are "Name:height". Files written before per-member
                // heights existed hold a bare name, which GroupMember.parse()
                // reads as the default height.
                List<GroupMember> members = new ArrayList<>();
                for (String entry : details.split(";")) {
                    GroupMember member = GroupMember.parse(entry);
                    if (member != null) {
                        members.add(member);
                    }
                }
                if (members.isEmpty()) {
                    return null;
                }
                return new GroupTicket(ticketId, visitorName, basePrice, members);
            default:
                return null;
        }
    }

    // -------------------------------------------------------------- visitors

    /**
     * Writes every registered visitor and the ticket they hold.
     *
     * @param visitors the visitors to write
     * @param filepath destination file
     * @throws DataPersistenceException when the write fails
     */
    public static void saveVisitors(List<Visitor> visitors, String filepath) throws DataPersistenceException {
        try (PrintWriter pw = new PrintWriter(new FileWriter(filepath))) {
            pw.println(VISITORS_HEADER);
            for (Visitor v : visitors) {
                String ticketId = v.getTicket() != null ? v.getTicket().getTicketId() : "NONE";
                pw.println(escapeCSV(v.getName()) + ","
                           + v.getAge() + ","
                           + v.getHeight() + ","
                           + escapeCSV(ticketId));
            }
        } catch (IOException e) {
            throw new DataPersistenceException(filepath, e);
        }
    }

    /**
     * {@link #saveVisitors(List, String)} that logs instead of throwing.
     *
     * @param visitors the visitors to write
     * @param filepath destination file
     */
    public static void saveVisitorsQuietly(List<Visitor> visitors, String filepath) {
        try {
            saveVisitors(visitors, filepath);
        } catch (DataPersistenceException e) {
            AuditLogger.getInstance().log("ERROR", "Failed to save visitors CSV: " + e.getMessage());
        }
    }

    /**
     * Reads visitors back and re-links them to their tickets.
     *
     * @param filepath      source file
     * @param loadedTickets the tickets already restored, for the ID lookup
     * @return the visitors read; empty when the file does not exist
     */
    public static List<Visitor> loadVisitors(String filepath, List<Ticket> loadedTickets) {
        List<Visitor> visitors = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new FileReader(filepath))) {
            br.readLine(); // header

            String line;
            while ((line = br.readLine()) != null) {
                if (line.trim().isEmpty()) {
                    continue;
                }
                try {
                    String[] t = splitCSV(line);
                    if (t.length < 4) {
                        continue;
                    }
                    Visitor v = new Visitor(t[0], Integer.parseInt(t[1]), Double.parseDouble(t[2]));
                    String ticketId = t[3];
                    if (!"NONE".equalsIgnoreCase(ticketId)) {
                        for (Ticket ticket : loadedTickets) {
                            if (ticket.hasId(ticketId)) {
                                v.setTicket(ticket);
                                break;
                            }
                        }
                    }
                    visitors.add(v);
                } catch (RuntimeException e) {
                    AuditLogger.getInstance().log("WARNING",
                        "Corrupted visitor row skipped: " + line + ". Error: " + e.getMessage());
                }
            }
        } catch (IOException e) {
            // No file yet - expected on a first run.
        }
        return visitors;
    }

    // --------------------------------------------------------------- helpers

    /**
     * Quotes a value if it contains a comma, quote or newline.
     *
     * @param value the raw value
     * @return the CSV-safe rendering
     */
    private static String escapeCSV(String value) {
        if (value == null) {
            return "";
        }
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    /**
     * Splits one CSV row, honouring quoted fields and un-escaping doubled quotes.
     *
     * <p>Symmetric with {@link #escapeCSV(String)} — the previous version toggled
     * a flag on every quote character and therefore turned {@code ""} back into
     * nothing instead of a single {@code "}.</p>
     *
     * @param line the raw row
     * @return the parsed fields, trimmed
     */
    private static String[] splitCSV(String line) {
        List<String> fields = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        boolean inQuotes = false;

        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                if (inQuotes && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    sb.append('"');
                    i++;
                } else {
                    inQuotes = !inQuotes;
                }
            } else if (c == ',' && !inQuotes) {
                fields.add(sb.toString().trim());
                sb.setLength(0);
            } else {
                sb.append(c);
            }
        }
        fields.add(sb.toString().trim());
        return fields.toArray(new String[0]);
    }
}
