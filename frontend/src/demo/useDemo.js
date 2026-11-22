import { useEffect, useState } from 'react';
import { readDemo } from './data';

export function useDemo() {
  const [data, setData] = useState(readDemo);
  useEffect(() => {
    const refresh = () => setData(readDemo());
    window.addEventListener('foodrescue:demo-change', refresh);
    return () => window.removeEventListener('foodrescue:demo-change', refresh);
  }, []);
  return data;
}
