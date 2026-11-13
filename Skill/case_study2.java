
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

// Main class
public class case_study2 {

    // 1. Currency
    enum Currency {
        INR, USD, EUR
    }

    // 2. Money - Value Object
    static final class Money {
        private final BigDecimal amount;
        private final Currency currency;

        private Money(BigDecimal amount, Currency currency) {
            if (amount.compareTo(BigDecimal.ZERO) < 0) {
                throw new IllegalArgumentException(
                    "Money cannot be negative"
                );
            }

            this.amount = amount.setScale(2);
            this.currency = Objects.requireNonNull(currency);
        }

        static Money of(String amount, Currency currency) {
            return new Money(new BigDecimal(amount), currency);
        }

        static Money zero(Currency currency) {
            return Money.of("0.00", currency);
        }

        Money plus(Money other) {
            if (currency != other.currency) {
                throw new IllegalArgumentException(
                    "Cannot add different currencies"
                );
            }

            return new Money(
                amount.add(other.amount), currency
            );
        }

        Money times(int quantity) {
            return new Money(
                amount.multiply(BigDecimal.valueOf(quantity)),
                currency
            );
        }

        @Override
        public String toString() {
            return currency + " " + amount;
        }
    }

    // 3. Order ID - Value Object
    static final class OrderId {
        private final String id;

        OrderId(String id) {
            if (id == null || id.isBlank()) {
                throw new IllegalArgumentException(
                    "Invalid order ID"
                );
            }
            this.id = id;
        }

        @Override
        public String toString() {
            return id;
        }
    }

    // 4. Product ID - Value Object
    static final class ProductId {
        private final String id;

        ProductId(String id) {
            if (id == null || id.isBlank()) {
                throw new IllegalArgumentException(
                    "Invalid product ID"
                );
            }
            this.id = id;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (!(obj instanceof ProductId)) return false;

            ProductId other = (ProductId) obj;
            return id.equals(other.id);
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

    // 5. Quantity - Value Object
    static final class Quantity {
        private final int value;

        Quantity(int value) {
            if (value <= 0) {
                throw new IllegalArgumentException(
                    "Quantity must be positive"
                );
            }
            this.value = value;
        }

        int getValue() {
            return value;
        }

        Quantity add(Quantity other) {
            return new Quantity(
                Math.addExact(value, other.value)
            );
        }
    }

    // 6. Order Line - Value Object
    static final class OrderLine {
        private final ProductId productId;
        private final Money unitPrice;
        private final Quantity quantity;

        OrderLine(ProductId productId,
                  Money unitPrice,
                  Quantity quantity) {

            this.productId = Objects.requireNonNull(productId);
            this.unitPrice = Objects.requireNonNull(unitPrice);
            this.quantity = Objects.requireNonNull(quantity);
        }

        OrderLine combine(OrderLine other) {
            if (!unitPrice.currency.equals(
                    other.unitPrice.currency)
                || unitPrice.amount.compareTo(
                    other.unitPrice.amount) != 0) {

                throw new IllegalArgumentException(
                    "Product price must match"
                );
            }

            return new OrderLine(
                productId,
                unitPrice,
                quantity.add(other.quantity)
            );
        }

        Money total() {
            return unitPrice.times(quantity.getValue());
        }

        @Override
        public String toString() {
            return productId + " | Price: " + unitPrice
                + " | Quantity: " + quantity.getValue()
                + " | Total: " + total();
        }
    }

    // 7. Order Status
    enum Status {
        DRAFT,
        PLACED,
        PAID,
        SHIPPED,
        DELIVERED,
        CANCELLED
    }

    // 8. Tracking Number - Value Object
    static final class TrackingNumber {
        private final String number;

        TrackingNumber(String number) {
            if (number == null || number.isBlank()) {
                throw new IllegalArgumentException(
                    "Invalid tracking number"
                );
            }
            this.number = number;
        }

        @Override
        public String toString() {
            return number;
        }
    }

    // 9. Payment Reference - Value Object
    static final class PaymentRef {
        private final String reference;

        PaymentRef(String reference) {
            if (reference == null || reference.isBlank()) {
                throw new IllegalArgumentException(
                    "Invalid payment reference"
                );
            }
            this.reference = reference;
        }
    }

    // 10. Shipment
    static final class Shipment {
        private final TrackingNumber trackingNumber;
        private final Instant shippedAt;

        Shipment(TrackingNumber trackingNumber, Instant shippedAt) {
            this.trackingNumber =
                Objects.requireNonNull(trackingNumber);
            this.shippedAt = Objects.requireNonNull(shippedAt);
        }

        @Override
        public String toString() {
            return "Tracking: " + trackingNumber
                + " | Shipped At: " + shippedAt;
        }
    }

    // 11. Custom Exception
    static class DomainException extends RuntimeException {
        DomainException(String message) {
            super(message);
        }
    }

    // 12. Order - Entity and Aggregate Root
    static final class Order {

        private final OrderId id;
        private Status status = Status.DRAFT;

        private final Map<ProductId, OrderLine> lines =
            new LinkedHashMap<>();

        private Optional<Shipment> shipment =
            Optional.empty();

        private Optional<PaymentRef> payment =
            Optional.empty();

        private final Currency currency;

        Order(OrderId id, Currency currency) {
            this.id = Objects.requireNonNull(id);
            this.currency = Objects.requireNonNull(currency);
        }

        // Check current status
        private void requireStatus(Status expected) {
            if (status != expected) {
                throw new DomainException(
                    "Operation not allowed. Current status: "
                    + status
                );
            }
        }

        // Add product (only in DRAFT)
        void addLine(ProductId productId,
                     Money unitPrice,
                     Quantity quantity) {

            requireStatus(Status.DRAFT);

            if (unitPrice.currency != currency) {
                throw new DomainException(
                    "Order currency mismatch"
                );
            }

            OrderLine newLine =
                new OrderLine(productId, unitPrice, quantity);

            lines.merge(
                productId,
                newLine,
                OrderLine::combine
            );
        }

        // Remove product (only in DRAFT)
        void removeLine(ProductId productId) {
            requireStatus(Status.DRAFT);

            if (lines.remove(productId) == null) {
                throw new DomainException(
                    "Product not found in order"
                );
            }
        }

        // Place order
        void place() {
            requireStatus(Status.DRAFT);

            if (lines.isEmpty()) {
                throw new DomainException(
                    "Cannot place an empty order"
                );
            }

            status = Status.PLACED;
        }

        // Make payment
        void pay(PaymentRef paymentRef) {
            requireStatus(Status.PLACED);

            payment = Optional.of(
                Objects.requireNonNull(paymentRef)
            );

            status = Status.PAID;
        }

        // Ship order
        void ship(TrackingNumber trackingNumber, Instant at) {
            requireStatus(Status.PAID);

            shipment = Optional.of(
                new Shipment(trackingNumber, at)
            );

            status = Status.SHIPPED;
        }

        // Deliver order
        void deliver(Instant at) {
            requireStatus(Status.SHIPPED);

            Objects.requireNonNull(at);
            status = Status.DELIVERED;

            System.out.println(
                "Delivered at: " + at
            );
        }

        // Cancel order
        void cancel() {
            if (status != Status.DRAFT &&
                status != Status.PLACED &&
                status != Status.PAID) {

                throw new DomainException(
                    "Cannot cancel an order in " + status
                );
            }

            status = Status.CANCELLED;
        }

        // Calculate total from order lines
        Money total() {
            Money result = Money.zero(currency);

            for (OrderLine line : lines.values()) {
                result = result.plus(line.total());
            }

            return result;
        }

        // Return read-only list of order lines
        List<OrderLine> getLines() {
            return List.copyOf(lines.values());
        }

        // Display order
        void display() {
            System.out.println("\nOrder ID: " + id);
            System.out.println("Status: " + status);

            System.out.println("\nOrder Lines:");

            for (OrderLine line : lines.values()) {
                System.out.println(line);
            }

            System.out.println("\nOrder Total: " + total());

            System.out.println("Payment Done: "
                + payment.isPresent());

            System.out.println("Shipment: "
                + shipment.map(Object::toString)
                          .orElse("Not shipped"));
        }

        String getStatus() {
            return status.toString();
        }

        OrderId getId() {
            return id;
        }
    }

    // 13. Main Method
    public static void main(String[] args) {

        // Create order
        Order order = new Order(
            new OrderId("ORD101"),
            Currency.INR
        );

        // Create products
        ProductId laptop = new ProductId("P101");
        ProductId mouse = new ProductId("P102");

        // Add products
        order.addLine(
            laptop,
            Money.of("50000.00", Currency.INR),
            new Quantity(1)
        );

        order.addLine(
            mouse,
            Money.of("1000.00", Currency.INR),
            new Quantity(2)
        );

        // Add same product again
        order.addLine(
            laptop,
            Money.of("50000.00", Currency.INR),
            new Quantity(1)
        );

        // Display draft order
        System.out.println("----- DRAFT ORDER -----");
        order.display();

        // Place order
        order.place();

        // Payment
        order.pay(new PaymentRef("PAY101"));

        // Ship order
        order.ship(
            new TrackingNumber("TRACK12345"),
            Instant.now()
        );

        // Display shipped order
        System.out.println("\n----- SHIPPED ORDER -----");
        order.display();

        // Deliver order
        order.deliver(Instant.now());

        // Final status
        System.out.println("\nFinal Status: "
            + order.getStatus());
    }
}
