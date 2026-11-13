
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;

// Main class
public class case_study4 {

    // 1. Room ID - Value Object
    static final class RoomId {
        private final String id;

        RoomId(String id) {
            if (id == null || id.isBlank()) {
                throw new IllegalArgumentException("Invalid room ID");
            }
            this.id = id;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (!(obj instanceof RoomId)) return false;
            return id.equals(((RoomId) obj).id);
        }

        @Override
        public int hashCode() {
            return Objects.hash(id);
        }

        @Override
        public String toString() {
            return id;
        }
    }

    // 2. Reservation ID - Value Object
    static final class ReservationId {
        private final String id;

        ReservationId(String id) {
            if (id == null || id.isBlank()) {
                throw new IllegalArgumentException(
                    "Invalid reservation ID"
                );
            }
            this.id = id;
        }

        @Override
        public String toString() {
            return id;
        }
    }

    // 3. Guest - Entity
    static final class Guest {
        private final String guestId;
        private final String name;

        Guest(String guestId, String name) {
            if (guestId == null || guestId.isBlank()
                    || name == null || name.isBlank()) {
                throw new IllegalArgumentException(
                    "Guest details are required"
                );
            }

            this.guestId = guestId;
            this.name = name;
        }

        String getName() {
            return name;
        }
    }

    // 4. Room - Entity
    static final class Room {
        private final RoomId id;
        private final String roomType;

        Room(RoomId id, String roomType) {
            this.id = Objects.requireNonNull(id);

            if (roomType == null || roomType.isBlank()) {
                throw new IllegalArgumentException(
                    "Room type is required"
                );
            }

            this.roomType = roomType;
        }

        RoomId getId() {
            return id;
        }

        String getRoomType() {
            return roomType;
        }
    }

    // 5. DateRange - Value Object
    // Check-in is inclusive; check-out is exclusive.
    static final class DateRange {
        private final LocalDate checkIn;
        private final LocalDate checkOut;

        DateRange(LocalDate checkIn, LocalDate checkOut) {
            this.checkIn = Objects.requireNonNull(checkIn);
            this.checkOut = Objects.requireNonNull(checkOut);

            if (!checkOut.isAfter(checkIn)) {
                throw new IllegalArgumentException(
                    "Check-out must be after check-in"
                );
            }
        }

        boolean overlaps(DateRange other) {
            return checkIn.isBefore(other.checkOut)
                && other.checkIn.isBefore(checkOut);
        }

        long nights() {
            return ChronoUnit.DAYS.between(checkIn, checkOut);
        }

        LocalDate getCheckIn() {
            return checkIn;
        }

        LocalDate getCheckOut() {
            return checkOut;
        }

        @Override
        public String toString() {
            return checkIn + " to " + checkOut;
        }
    }

    // 6. Payment Guarantee - Value Object
    static final class PaymentGuarantee {
        private final String reference;
        private final BigDecimal guaranteedAmount;

        PaymentGuarantee(String reference, BigDecimal amount) {
            if (reference == null || reference.isBlank()) {
                throw new IllegalArgumentException(
                    "Payment reference is required"
                );
            }

            if (amount == null
                    || amount.compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException(
                    "Guarantee amount must be positive"
                );
            }

            this.reference = reference;
            this.guaranteedAmount = amount;
        }

        boolean covers(BigDecimal bookingAmount) {
            return guaranteedAmount.compareTo(bookingAmount) >= 0;
        }
    }

    // 7. Cancellation Policy - Value Object
    static final class CancellationPolicy {

        BigDecimal calculateRefund(
                BigDecimal bookingAmount,
                LocalDate checkIn,
                LocalDate cancellationDate) {

            if (cancellationDate.isBefore(checkIn)) {
                long daysBefore = ChronoUnit.DAYS.between(
                    cancellationDate, checkIn
                );

                if (daysBefore >= 7) {
                    return bookingAmount;
                } else if (daysBefore >= 2) {
                    return bookingAmount.multiply(
                        new BigDecimal("0.50")
                    );
                }
            }

            return BigDecimal.ZERO;
        }
    }

    // 8. Reservation Status
    enum Status {
        REQUESTED,
        CONFIRMED,
        CHECKED_IN,
        COMPLETED,
        CANCELLED
    }

    // 9. Reservation - Entity and Aggregate Root
    static final class Reservation {

        private final ReservationId id;
        private final Guest guest;
        private final Room room;
        private final DateRange stay;
        private final BigDecimal bookingAmount;

        private Status status = Status.REQUESTED;
        private Optional<PaymentGuarantee> guarantee =
            Optional.empty();

        private BigDecimal refund = BigDecimal.ZERO;

        Reservation(
                ReservationId id,
                Guest guest,
                Room room,
                DateRange stay,
                BigDecimal bookingAmount) {

            this.id = Objects.requireNonNull(id);
            this.guest = Objects.requireNonNull(guest);
            this.room = Objects.requireNonNull(room);
            this.stay = Objects.requireNonNull(stay);

            if (bookingAmount == null
                    || bookingAmount.compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException(
                    "Booking amount must be positive"
                );
            }

            this.bookingAmount = bookingAmount;
        }

        private void requireStatus(Status expected) {
            if (status != expected) {
                throw new IllegalStateException(
                    "Invalid operation. Current status: " + status
                );
            }
        }

        // Confirm only when payment guarantee exists
        void confirm(PaymentGuarantee paymentGuarantee) {
            requireStatus(Status.REQUESTED);

            Objects.requireNonNull(paymentGuarantee);

            if (!paymentGuarantee.covers(bookingAmount)) {
                throw new IllegalStateException(
                    "Payment guarantee is insufficient"
                );
            }

            guarantee = Optional.of(paymentGuarantee);
            status = Status.CONFIRMED;
        }

        // Check in only during the reserved stay
        void checkIn(LocalDate today) {
            requireStatus(Status.CONFIRMED);

            if (today.isBefore(stay.getCheckIn())
                    || !today.isBefore(stay.getCheckOut())) {
                throw new IllegalStateException(
                    "Check-in is outside the reservation period"
                );
            }

            status = Status.CHECKED_IN;
        }

        // Check out on or after the reserved checkout date
        void checkOut(LocalDate today) {
            requireStatus(Status.CHECKED_IN);

            if (today.isBefore(stay.getCheckOut())) {
                throw new IllegalStateException(
                    "Checkout date has not arrived"
                );
            }

            status = Status.COMPLETED;
        }

        // Cancel only before check-in
        BigDecimal cancel(
                LocalDate today,
                CancellationPolicy policy) {

            if (status != Status.REQUESTED
                    && status != Status.CONFIRMED) {
                throw new IllegalStateException(
                    "This reservation cannot be cancelled"
                );
            }

            if (!today.isBefore(stay.getCheckIn())) {
                throw new IllegalStateException(
                    "Cancellation is not allowed on or after check-in"
                );
            }

            refund = policy.calculateRefund(
                bookingAmount,
                stay.getCheckIn(),
                today
            );

            status = Status.CANCELLED;

            return refund;
        }

        Status getStatus() {
            return status;
        }

        RoomId getRoomId() {
            return room.getId();
        }

        DateRange getStay() {
            return stay;
        }

        ReservationId getId() {
            return id;
        }

        void display() {
            System.out.println("\nReservation ID: " + id);
            System.out.println("Guest: " + guest.getName());
            System.out.println("Room: " + room.getId());
            System.out.println("Stay: " + stay);
            System.out.println("Nights: " + stay.nights());
            System.out.println("Amount: Rs. " + bookingAmount);
            System.out.println("Status: " + status);
            System.out.println("Refund: Rs. " + refund);
            System.out.println(
                "Payment guarantee: "
                + guarantee.isPresent()
            );
        }
    }

    // 10. Reservation Manager
    // Synchronization protects the in-memory booking check.
    static final class ReservationManager {

        private final List<Reservation> reservations =
            new ArrayList<>();

        synchronized Reservation request(
                ReservationId id,
                Guest guest,
                Room room,
                DateRange stay,
                BigDecimal amount) {

            // Check all active reservations for this room
            for (Reservation existing : reservations) {

                if (existing.getRoomId().equals(room.getId())
                        && existing.getStatus() != Status.CANCELLED
                        && existing.getStay().overlaps(stay)) {

                    throw new IllegalStateException(
                        "Room is already reserved for these dates"
                    );
                }
            }

            Reservation reservation = new Reservation(
                id, guest, room, stay, amount
            );

            reservations.add(reservation);
            return reservation;
        }
    }

    // 11. Main Method
    public static void main(String[] args) {

        ReservationManager manager =
            new ReservationManager();

        Guest guest = new Guest("G101", "Rahul");

        Room room = new Room(
            new RoomId("ROOM101"),
            "Deluxe"
        );

        LocalDate today = LocalDate.now();

        DateRange stay = new DateRange(
            today.plusDays(5),
            today.plusDays(8)
        );

        // Create reservation
        Reservation reservation = manager.request(
            new ReservationId("RES101"),
            guest,
            room,
            stay,
            new BigDecimal("15000.00")
        );

        System.out.println("----- RESERVATION CREATED -----");
        reservation.display();

        // Confirm reservation
        reservation.confirm(
            new PaymentGuarantee(
                "PAY101",
                new BigDecimal("15000.00")
            )
        );

        System.out.println("\n----- RESERVATION CONFIRMED -----");
        reservation.display();

        // Check in on check-in date
        reservation.checkIn(stay.getCheckIn());

        System.out.println("\n----- GUEST CHECKED IN -----");
        reservation.display();

        // Check out on check-out date
        reservation.checkOut(stay.getCheckOut());

        System.out.println("\n----- GUEST CHECKED OUT -----");
        reservation.display();

        // Demonstrate consecutive date ranges
        DateRange firstStay = new DateRange(
            LocalDate.of(2026, 12, 10),
            LocalDate.of(2026, 12, 12)
        );

        DateRange secondStay = new DateRange(
            LocalDate.of(2026, 12, 12),
            LocalDate.of(2026, 12, 15)
        );

        System.out.println(
            "\nConsecutive stays overlap: "
            + firstStay.overlaps(secondStay)
        );
    }
}
