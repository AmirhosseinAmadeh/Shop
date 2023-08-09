package Interface;

import java.util.Date;

public interface Product {
    // double price = 0;
    // ProductName name = null;
    // Date MFG = null;//manufacturing date
    // Date EXP = null;//Expiration date

    double getPrice();

    String getName();

    String getCompany();

    Date getMFG();

    Date getEXP();

    int getId();// ًQRcode || BARcode

}
