package Models.Store;

import Database.SplayTree;
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


