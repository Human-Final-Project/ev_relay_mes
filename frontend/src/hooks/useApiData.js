import { useCallback, useEffect, useState } from "react";

export default function useApiData(loader, dependencies = []) {
  const [data, setData] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);

    try {
      const response = await loader();
      setData(response.data);
    } catch (reason) {
      setError(reason);
    } finally {
      setLoading(false);
    }
    // loader의 실행 조건은 이 훅을 사용하는 화면이 dependencies로 지정한다.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, dependencies);

  useEffect(() => {
    load();
  }, [load]);

  return { data, setData, loading, error, reload: load };
}
