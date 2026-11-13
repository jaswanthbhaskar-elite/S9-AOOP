
import java.util.*;

// 1. Currency - Known set of currencies
enum Currency {
    EUR(2),
    USD(2),
    JPY(0),
    KWD(3),
    INR(2);

    final int minorUnits;

    Currency(int minorUnits) {
        this.minorUnits = minorUnits;
    }
}

// 2. Money - Value Object
final class Money {
    private final long minor;
    private final Currency currency;

    private Money(long minor, Currency currency) {
        if (minor < 0)
            throw new IllegalArgumentException("Negative money");

        this.minor = minor;
        this.currency = Objects.requireNonNull(currency);
    }

    public static Money of(String decimal, Currency currency) {
        java.math.BigDecimal amount = new java.math.BigDecimal(decimal);

        if (amount.signum() < 0)
            throw new IllegalArgumentException("Negative amount");

        amount = amount.setScale(
            currency.minorUnits,
            java.math.RoundingMode.UNNECESSARY
        );

        long minor = amount
            .movePointRight(currency.minorUnits)
            .longValueExact();

        return new Money(minor, currency);
    }

    public Money plus(Money other) {
        requireSame(other);
        return new Money(
            Math.addExact(minor, other.minor), currency
        );
    }

    public Money times(Quantity quantity) {
        return new Money(
            Math.multiplyExact(minor, quantity.value()),
            currency
        );
    }

    public Money minus(Percentage percentage) {
        long result = java.math.BigDecimal.valueOf(minor)
            .multiply(java.math.BigDecimal.valueOf(
                10000 - percentage.basisPoints()
            ))
            .divide(
                java.math.BigDecimal.valueOf(10000),
                0,
                java.math.RoundingMode.HALF_EVEN
            )
            .longValueExact();

        return new Money(result, currency);
    }

    private void requireSame(Money other) {
        if (currency != other.currency)
            throw new IllegalArgumentException("Currency mismatch");
    }

    public long getMinor() {
        return minor;
    }

    public Currency getCurrency() {
        return currency;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof Money)) return false;

        Money other = (Money) obj;

        return minor == other.minor &&
               currency == other.currency;
    }

    @Override
    public int hashCode() {
        return Objects.hash(minor, currency);
    }

    @Override
    public String toString() {
        return currency + " " +
            java.math.BigDecimal.valueOf(minor, currency.minorUnits)
                .toPlainString();
    }
}

// 3. Percentage - Value Object
final class Percentage {
    private final int basisPoints;

    private Percentage(int basisPoints) {
        this.basisPoints = basisPoints;
    }

    public static Percentage ofBasisPoints(int bp) {
        if (bp < 0 || bp > 10000)
            throw new IllegalArgumentException("Invalid percentage");

        return new Percentage(bp);
    }

    public int basisPoints() {
        return basisPoints;
    }

    public Money applyTo(Money money) {
        return money.minus(
            ofBasisPoints(10000 - basisPoints)
        );
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof Percentage)) return false;

        Percentage other = (Percentage) obj;
        return basisPoints == other.basisPoints;
    }

    @Override
    public int hashCode() {
        return Objects.hash(basisPoints);
    }
}

// 4. Quantity - Value Object
final class Quantity {
    private final int value;

    private Quantity(int value) {
        this.value = value;
    }

    public static Quantity of(int value) {
        if (value < 1)
            throw new IllegalArgumentException("Quantity must be positive");

        return new Quantity(value);
    }

    public int value() {
        return value;
    }

    public Quantity plus(Quantity other) {
        return Quantity.of(Math.addExact(value, other.value));
    }
}

// 5. Product - Entity
class Product {
    private final String sku;
    private String name;
    private Money price;
    private Percentage discount;

    public Product(String sku, String name, Money price,
                   Percentage discount) {
        this.sku = sku;
        this.name = name;
        this.price = price;
        this.discount = discount;
    }

    public Money getFinalPrice() {
        return discount.applyTo(price);
    }

    public String getSku() {
        return sku;
    }

    public String getName() {
        return name;
    }
}

// 6. CartItem
class CartItem {
    private final Product product;
    private final Quantity quantity;

    public CartItem(Product product, Quantity quantity) {
        this.product = product;
        this.quantity = quantity;
    }

    public Money getTotal() {
        return product.getFinalPrice().times(quantity);
    }
}

// 7. Cart - Entity / Aggregate Root
class Cart {
    private final String cartId;
    private final List<CartItem> items = new ArrayList<>();

    public Cart(String cartId) {
        this.cartId = cartId;
    }
    public String getCartId() {
    return cartId;
}

    public void addItem(CartItem item) {
        items.add(item);
    }

    public Money getTotal() {
        if (items.isEmpty())
            throw new IllegalStateException("Cart is empty");

        Money total = items.get(0).getTotal();

        for (int i = 1; i < items.size(); i++) {
            total = total.plus(items.get(i).getTotal());
        }

        return total;
    }
}

// 8. Price - Non-negative amount
final class Price {
    private final Money money;

    public Price(Money money) {
        this.money = Objects.requireNonNull(money);
    }

    public Money getMoney() {
        return money;
    }
}

// 9. SignedMoney - Allows negative values for refunds
final class SignedMoney {
    private final long minor;
    private final Currency currency;

    public SignedMoney(long minor, Currency currency) {
        this.minor = minor;
        this.currency = Objects.requireNonNull(currency);
    }

    public SignedMoney plus(SignedMoney other) {
        if (currency != other.currency)
            throw new IllegalArgumentException("Currency mismatch");

        return new SignedMoney(
            Math.addExact(minor, other.minor), currency
        );
    }
}

// 10. Main class
public class case_study1 {
    public static void main(String[] args) {

        Money price = Money.of("1000.00", Currency.INR);

        Percentage discount =
            Percentage.ofBasisPoints(1000); // 10%

        Product product = new Product(
            "P101",
            "Laptop",
            price,
            discount
        );

        Quantity quantity = Quantity.of(2);

        Cart cart = new Cart("C101");

        cart.addItem(new CartItem(product, quantity));

        System.out.println("Product: " + product.getName());
        System.out.println("Price: " + price);
        System.out.println("Discount: 10%");
        System.out.println("Quantity: " + quantity.value());
        System.out.println("Cart Total: " + cart.getTotal());

        SignedMoney refund =
            new SignedMoney(-5000, Currency.INR);

        System.out.println("Refund: " + refund);
    }
}
