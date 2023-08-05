package Models.Store.CashDesk;

import Interface.Item;

public class IM implements Item {
    String name;
    Double price;
    int id;
    

    public IM(String name, int id) {
        this.name = name;
        this.id = id;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public int getId() {
        return id;
    }
    @Override
    public Double getPrice() {
        return price;
    }
    

    
}
