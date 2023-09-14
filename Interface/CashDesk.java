package Interface;

import java.util.ArrayList;

public interface CashDesk {
    void newShopingList();
    void removefromShopingList(Product product);
    ArrayList<Product> getShopingList();
    void PurchaseConfirmation(ArrayList<Product> ShopingList);
}
