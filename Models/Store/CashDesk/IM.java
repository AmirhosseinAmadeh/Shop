package Models.Store.CashDesk;

import Interface.Item;

public class IM implements Item {
    String name;
    String company;
    Double price;
    Integer counter;

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
    public Integer getCounter() {
        return counter;
    }

    public IM(String name, String company, Double price) {
        this.name = name;
        this.company = company;
        this.price = price;
        this.counter = 1;
    }

    public void oneMore() {
        counter++;
    }

    @Override
    public Double getFinalPrice() {
        return price * counter;
    }

}
