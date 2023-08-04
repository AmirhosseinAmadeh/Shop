package Models.Store.CashDesk;

import java.util.ArrayList;

import Interface.CashDesk;
import Interface.Product;

public class CD implements CashDesk {

    SL list = new SL();

    @Override
    public void addtoShopingList(Product product) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'addtoShopingList'");
    }

    @Override
    public void removefromShopingList(Product product) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'removefromShopingList'");
    }

    @Override
    public ArrayList<Product> getShopingList() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'getShopingList'");
    }

    @Override
    public void PurchaseConfirmation(ArrayList<Product> ShopingList) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'PurchaseConfirmation'");
    }
    
}
