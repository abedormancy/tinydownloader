package ga.uuid.app;

import java.util.Collection;
import java.util.Iterator;
import java.util.function.Supplier;

/**
 * 自定义列表，用于实时数据展示
 * @author abeholder
 *
 * @param <E>
 */
public class FixedList<E> implements Collection<E> {
	
	// 元素数组
	private Object[] element;
	// 列表固定容量
	private int fixedCapacity;
	// 当前空元素游标位置
	private transient int freeCursor = 0;
	
	public FixedList(int capacity) {
		if (capacity < 1) {
			throw new IllegalArgumentException("capacity < 1");
		}
		fixedCapacity = capacity;
		element = new Object[fixedCapacity];
	}
	
	@Override
	public boolean add(E e) {
		if (freeCursor < fixedCapacity) {
			element[freeCursor++] = e;
		}
		return false;
	}
	
	@SuppressWarnings("unchecked")
	public E get(int index) {
		return (E) element[index];
	}
	
	public E get(int index, Supplier<E> supplier) {
		E e = get(index);
		return e == null ? supplier.get() : e;
	}
	
	@Override
	public boolean remove(Object o) {
		if (o == null) return true;
		
		for (int i = 0; i < fixedCapacity; i++) {
			if (o.equals(element[i])) {
				element[i] = null;
				if (i < freeCursor) freeCursor = i;
				return true;
			}
		}
		return false;
	}
	
	@Override
	public int size() {
		return fixedCapacity;
	}

	@Override
	public boolean isEmpty() {
		return stream().allMatch(s -> s == null);
	}
	
	@Override
	public void clear() {
		for (int i = 0; i < fixedCapacity; i++) {
			element[i] = null;
		}
	}

	@Override
	public Iterator<E> iterator() {
		return new Itr();
	}

	private class Itr implements Iterator<E> {
		int cursor;
		int lastRet = -1;

		@Override
		public boolean hasNext() {
			return cursor < fixedCapacity;
		}

		@Override
		public E next() {
			return FixedList.this.get(lastRet = cursor++);
		}

		@Override
		public void remove() {
			if (lastRet != -1) {
				FixedList.this.remove(lastRet);
				lastRet = -1;
			} else {
				System.out.println("error");
			}
		}
	}
	
	@Override
	public boolean contains(Object o) {
		throw new UnsupportedOperationException();
	}
	
	@Override
	public Object[] toArray() {
		throw new UnsupportedOperationException();
	}

	@Override
	public <T> T[] toArray(T[] a) {
		throw new UnsupportedOperationException();
	}

	@Override
	public boolean containsAll(Collection<?> c) {
		throw new UnsupportedOperationException();
	}

	@Override
	public boolean addAll(Collection<? extends E> c) {
		throw new UnsupportedOperationException();
	}

	@Override
	public boolean removeAll(Collection<?> c) {
		throw new UnsupportedOperationException();
	}

	@Override
	public boolean retainAll(Collection<?> c) {
		throw new UnsupportedOperationException();
	}
}
