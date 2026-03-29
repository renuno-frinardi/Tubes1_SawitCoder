package alternative_bots_2.util;

/**
 * Simple fixed size stack helper class
 */
public class Stack<T> {
  
  private T[] data;
  private int index = 0;
  
  public Stack(int max_size) {
    data = (T[]) new Object[max_size];
  }

  public boolean push(T item) {
    if (index == data.length) { return false; }
    data[index++] = item;
    return true;
  }

  public T pop() {
    return (index == 0 ? null : data[--index]);
  }

  public T top() {
    return (index == 0 ? null : data[index - 1]);
  }

  public T bottom() {
    return (index == 0 ? null : data[0]);
  }

  public int capacity() { return data.length; }
  public int used() { return index; }
  public int available() { return data.length - index; }
  public boolean empty() { return index == 0; }
  public void clear() { index = 0; }
}

// Credits: justinottesen/battlecode25