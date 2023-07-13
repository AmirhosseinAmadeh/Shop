package Interface;

public interface StoreRoom {
    Product search();
    void add(Product product);
    void remove(Product product);
    void Update(Product product);
}
