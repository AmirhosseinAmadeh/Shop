package Models.Store;

import java.util.ArrayList;

import Database.SplayTree;
import Interface.Product;
import Interface.StoreRoom;

public class SR implements StoreRoom {
SplayTree list;
int Capacity;
public SR(int capacity) {
    this.list = new SplayTree();
    Capacity = capacity;
}
public void setCapacity(int capacity) {
    Capacity = capacity;
}
  
}


