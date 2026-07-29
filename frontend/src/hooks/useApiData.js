import { useCallback, useEffect, useRef, useState } from "react";

export default function useApiData(loader, dependencies = []) {
  const [data, setData] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const mountedRef = useRef(false);
  const requestSequenceRef = useRef(0);

  useEffect(() => {
    mountedRef.current = true;
    return () => {
      mountedRef.current = false;
      requestSequenceRef.current += 1;
    };
  }, []);

  const load = useCallback(async () => {
    const requestSequence = ++requestSequenceRef.current;
    if (mountedRef.current) {
      setLoading(true);
      setError(null);
    }
    try {
      const response = await loader();
      if (mountedRef.current && requestSequence === requestSequenceRef.current) {
        setData(response.data);
      }
      return response;
    } catch (reason) {
      if (mountedRef.current && requestSequence === requestSequenceRef.current) {
        setError(reason);
      }
      return undefined;
    } finally {
      if (mountedRef.current && requestSequence === requestSequenceRef.current) {
        setLoading(false);
      }
    }
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, dependencies);

  useEffect(() => { load(); }, [load]);
  return { data, setData, loading, error, reload: load };
}
