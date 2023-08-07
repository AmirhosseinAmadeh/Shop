package Models.Store.CashDesk;

import Interface.Item;

public class IM implements Item {
    String name;
    String company;
    Double price;
    int counter;
    @Override
    public String getName() {
        return name;
    }
    @Override
    public String getCompany() {
        return company;
    }
    @Override
    public Double getPrice() {
        return price;
    }
    @Override
    public int getCounter() {
        return counter;
    }
    


    
}
