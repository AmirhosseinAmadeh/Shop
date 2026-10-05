package shop;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDate;

import shop.service.Persistence;
import shop.service.Shop;
import shop.web.ApiServer;

/** Entry point: {@code java shop.Main [--port 8080] [--host 127.0.0.1] [--data shop-data.json] [--seed]}. */
public final class Main {
    private Main() {}

    public static void main(String[] args) throws IOException {
        int port = 8080;
        String host = "127.0.0.1";
        Path data = Path.of("shop-data.json");
        boolean seed = false;
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--port" -> port = Integer.parseInt(args[++i]);
                case "--host" -> host = args[++i];
                case "--data" -> data = Path.of(args[++i]);
                case "--seed" -> seed = true;
                default -> {
                    System.err.println("usage: java shop.Main [--port N] [--host H] [--data FILE] [--seed]");
                    System.exit(2);
                }
            }
        }

        Shop shop = new Shop(Clock.systemDefaultZone());
        if (Files.exists(data)) {
            Persistence.load(shop, data);
            System.out.println("Loaded " + data);
        } else if (seed) {
            seed(shop);
            System.out.println("Seeded demo data");
        }

        final Path file = data;
        shop.setOnChange(() -> {
            try {
                Persistence.save(shop, file);
            } catch (IOException e) {
                System.err.println("warning: could not save " + file + ": " + e.getMessage());
            }
        });
        Persistence.save(shop, file);

        ApiServer server = new ApiServer(shop, host, port);
        server.start();
        System.out.println("FreshShop running at http://" + host + ":" + server.port() + "/");
    }

    /** Demo catalog with batches spread around today so every expiry rule is visible. */
    static void seed(Shop shop) {
        LocalDate t = shop.today();
        shop.addProduct("MLK-1L", "Whole Milk 1L", "Dairy", 189);
        shop.addProduct("YOG-500", "Greek Yogurt 500g", "Dairy", 349);
        shop.addProduct("CHS-200", "Cheddar Cheese 200g", "Dairy", 429);
        shop.addProduct("BRD-WHL", "Wholemeal Bread", "Bakery", 259);
        shop.addProduct("CRS-6", "Butter Croissants (6)", "Bakery", 399);
        shop.addProduct("APL-1KG", "Red Apples 1kg", "Produce", 299);
        shop.addProduct("SLD-MIX", "Mixed Salad Bag", "Produce", 249);
        shop.addProduct("EGG-12", "Free-range Eggs (12)", "Dairy", 449);
        shop.addProduct("RIC-2KG", "Basmati Rice 2kg", "Pantry", 699);
        shop.addProduct("PST-500", "Spaghetti 500g", "Pantry", 179);
        shop.addProduct("TIN-TOM", "Chopped Tomatoes 400g", "Pantry", 119);

        shop.receive("MLK-1L", 12, t.minusDays(6), t.plusDays(1));
        shop.receive("MLK-1L", 30, t.minusDays(1), t.plusDays(9));
        shop.receive("YOG-500", 14, t.minusDays(10), t.plusDays(3));
        shop.receive("YOG-500", 20, t.minusDays(2), t.plusDays(21));
        shop.receive("CHS-200", 18, t.minusDays(20), t.plusDays(40));
        shop.receive("BRD-WHL", 10, t.minusDays(2), t.plusDays(1));
        shop.receive("BRD-WHL", 16, t, t.plusDays(4));
        shop.receive("CRS-6", 8, t.minusDays(1), t.plusDays(2));
        shop.receive("APL-1KG", 40, t.minusDays(5), t.plusDays(12));
        shop.receive("SLD-MIX", 15, t.minusDays(3), t.plusDays(5));
        shop.receive("EGG-12", 24, t.minusDays(4), t.plusDays(17));
        shop.receive("RIC-2KG", 25, t.minusMonths(2), t.plusMonths(14));
        shop.receive("PST-500", 60, t.minusMonths(3), t.plusMonths(20));
        shop.receive("TIN-TOM", 48, null, null);
    }
}
