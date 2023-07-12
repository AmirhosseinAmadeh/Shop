package Interface;

import java.util.Date;

public interface Product {
    double price = 0;
    String name = null;
    Date MFG = null;//manufacturing date
    Date EXP = null;//Expiration date

     double getPrice();
    
}
