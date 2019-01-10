package xyz.wildseries.wildchests.key;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

public class Key {

    private String key;

    private Key(String key){
        this.key = key;
    }

    @Override
    public String toString() {
        return key;
    }

    public static Key of(ItemStack itemStack){
        return of(itemStack.getType(), itemStack.getDurability());
    }

    public static Key of(Material material, short data){
        return of(material + ":" + data);
    }

    public static Key of(String key){
        return new Key(key);
    }

}
