import java.util.*;

class SimpleYesTransform {

    public void doStuff() {
        ArrayList<String> list = new ArrayList<>();
        list.add("hello");
        list.add(" ");
        list.add("world");
        list.add("!");

        list.size();

        // should be transformed
        for (String s : list) {
            System.out.print(s);
        }
    }

    public void callsSize() {
        ArrayList<String> list = new ArrayList<>();

        // should be transformed
        for (String s : list) {
            list.size();
        }
    }

    public void doStuffNotAnArrayList() {
        List<String> list = new LinkedList<>();
        list.add("hello");
        list.add(" ");
        list.add("world");
        list.add("!");

        list.size();

        // should not be transformed
        for (String s : list) {
            System.out.print(s);
        }
    }

    public void doStuffNotAList() {
        Set<String> list = new HashSet<>();
        list.add("hello");
        list.add(" ");
        list.add("world");
        list.add("!");

        list.size();

        // should not be transformed
        for (String s : list) {
            System.out.print(s);
        }
    }

    public void doStuffWithValueFromOtherPlace1() {
        List<String> list = getList();
        list.add("hello");
        list.add(" ");
        list.add("world");
        list.add("!");

        list.size();

        // should not be transformed
        for (String s : list) {
            System.out.print(s);
        }
    }

    public void doStuffWithValueFromOtherPlace2(List<String> list) {
        list.add("hello");
        list.add(" ");
        list.add("world");
        list.add("!");

        list.size();

        // should not be transformed
        for (String s : list) {
            System.out.print(s);
        }
    }

    public void reassignList() {
        ArrayList<String> list = new ArrayList<>();
        list.add("hello");
        list.add(" ");
        list.add("world");
        list.add("!");

        list.size();
        list = new LinkedList<>();

        // should not be transformed
        for (String s : list) {
            System.out.print(s);
        }
    }

    public void aliasList() {
        ArrayList<String> list = new ArrayList<>();
        list.add("hello");
        list.add(" ");
        list.add("world");
        list.add("!");

        list.size();

        List<String> alias = list;

        // should not be transformed
        for (String s : list) {
            System.out.print(s);
        }
    }

    public void aliasList2() {
        ArrayList<String> list = new ArrayList<>();
        list.add("hello");
        list.add(" ");
        list.add("world");
        list.add("!");

        list.size();

        List<String> alias;
        alias = list;

        // should not be transformed
        for (String s : list) {
            System.out.print(s);
        }
    }

    public void aliasListViaCall() {
        ArrayList<String> list = new ArrayList<>();
        list.add("hello");
        list.add(" ");
        list.add("world");
        list.add("!");

        list.size();

        mightAliasList(list);

        // should not be transformed
        for (String s : list) {
            System.out.print(s);
        }
    }

    public void callsAdd() {
        ArrayList<String> list = new ArrayList<>();

        // should not be transformed
        for (String s : list) {
            list.add(null);
        }
    }

    public void callsAddAll() {
        ArrayList<String> list = new ArrayList<>();

        // should not be transformed
        for (String s : list) {
            list.addAll(Collections.EMPTY_LIST);
        }
    }

    public void callsRemove() {
        ArrayList<String> list = new ArrayList<>();

        // should not be transformed
        for (String s : list) {
            list.remove(null);
        }
    }

    public void callsRemoveAll() {
        ArrayList<String> list = new ArrayList<>();

        // should not be transformed
        for (String s : list) {
            list.removeAll(Collections.EMPTY_LIST);
        }
    }

    public void callsRemoveIf() {
        ArrayList<String> list = new ArrayList<>();

        // should not be transformed
        for (String s : list) {
            list.removeIf(b -> false);
        }
    }

    public void callsRemoveRange() {
        ArrayList<String> list = new ArrayList<>();

        // should not be transformed
        for (String s : list) {
            list.removeRange(0, 0);
        }
    }

    public void callsRetainAll() {
        ArrayList<String> list = new ArrayList<>();

        // should not be transformed
        for (String s : list) {
            list.retainAll(Collections.EMPTY_LIST);
        }
    }

    private List getList() {
        return new LinkedList();
    }

    private void mightAliasList(List list) {

    }

}