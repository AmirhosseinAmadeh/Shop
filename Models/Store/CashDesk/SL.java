package Models.Store.CashDesk;

import java.util.ArrayList;
import java.util.List;
import Interface.ShopingList;
import Models.Product.Pt;

public class SL implements ShopingList {

    List<IM> list = new ArrayList<IM>();

    @Override
    public void Display() {
        System.out.println("--------------------------------");
        int count = 0;
        for (IM im : list) {
            System.out.print(count++);
            System.out.println(". | ".concat(im.getName()).concat(" | ").concat(im.getPrice().toString()));
        }
    }

    public void add(Pt product) {
        IM tempItem = new IM(product.getName(), product.getCompany(), product.getPrice());
        // new item
        int place = list.indexOf(tempItem);
        // find the place where the item is
        if (place != -1) {
            list.get(place).oneMore();
            // add one to exsting item
        } else
            list.add(tempItem);
        // add new item
    }

}
