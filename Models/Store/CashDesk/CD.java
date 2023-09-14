package Models.Store.CashDesk;

import java.util.ArrayList;
import java.util.List;

import Interface.CashDesk;
import Interface.Product;

public class CD implements CashDesk {

    List<SL> list = new ArrayList<>();// shoping lists of  day

    @Override
    public void newShopingList() {
        SL shopingListSl = new SL();
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
