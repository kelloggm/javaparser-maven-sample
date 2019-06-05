import java.util.*;

class SimpleYesTransform {

    public void doStuff() {
        List<String> list = new ArrayList<>();
        list.add("hello");
        list.add(" ");
        list.add("world");
        list.add("!");

        list.size();

        for (String s : list) {
            System.out.print(s);
        }
    }

}