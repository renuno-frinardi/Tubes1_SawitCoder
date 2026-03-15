package alternative_bots_2.util;

/**
 * Simple fixed size queue helper class
 */
public class Queue<T> {

  private T[] data;
  private int head = 0;
  private int tail = 0;
  private int size = 0;

  public Queue(int max_size) {
    data = (T[]) new Object[max_size];
  }

  public boolean enqueue(T item) {
    if (size == data.length) { return false; }
    data[tail] = item;
    tail = (tail + 1) % data.length;
    size++;
    return true;
  }

  public T dequeue() {
    if (size == 0) { return null; }
    T item = data[head];
    head = (head + 1) % data.length;
    size--;
    return item;
  }

  public T front() {
    return (size == 0 ? null : data[head]);
  }

  public T back() {
    return (size == 0 ? null : data[(tail - 1 + data.length) % data.length]);
  }

  public T popBack() {
    if (size == 0) { return null; }
    tail = (tail - 1 + data.length) % data.length;
    T item = data[tail];
    size--;
    return item;
  }
  
  public int capacity() { return data.length; }
  public int used() { return size; }
  public int available() { return data.length - size; }
  public boolean empty() { return size == 0; }
  public void clear() { head = 0; tail = 0; size = 0; }
}

// Credits: justinottesen/battlecode25