package Models.Store.CashDesk;

import java.util.ArrayList;

import Interface.ShopingList;

public class SL implements ShopingList {

    ArrayList<IM> list = new ArrayList<IM>();

    @Override
    public void Display() {
        System.out.println("--------------------------------");
        int count = 0;
        for (IM im : list) {
            System.out.print(count++);
            System.out.println(". | ".concat(im.getName()).concat(" | ").concat(im.getPrice().toString()));
        }
    }
    
}
