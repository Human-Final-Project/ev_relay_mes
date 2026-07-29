import { act, fireEvent, render, screen, waitFor } from "@testing-library/react";
import useApiData from "../hooks/useApiData";

function deferred() {
  let resolve;
  let reject;
  const promise = new Promise((resolvePromise, rejectPromise) => {
    resolve = resolvePromise;
    reject = rejectPromise;
  });
  return { promise, resolve, reject };
}

function ApiDataHarness({ loader }) {
  const { data, loading, error, reload } = useApiData(loader, [loader]);
  return <>
    <span data-testid="data">{data || "-"}</span>
    <span data-testid="loading">{String(loading)}</span>
    <span data-testid="error">{error?.message || "-"}</span>
    <button onClick={reload}>reload</button>
  </>;
}

test("an older response cannot overwrite the latest request result", async () => {
  const first = deferred();
  const second = deferred();
  const loader = jest.fn()
    .mockReturnValueOnce(first.promise)
    .mockReturnValueOnce(second.promise);

  render(<ApiDataHarness loader={loader}/>);
  await waitFor(() => expect(loader).toHaveBeenCalledTimes(1));

  fireEvent.click(screen.getByText("reload"));
  expect(loader).toHaveBeenCalledTimes(2);

  await act(async () => {
    second.resolve({ data: "latest" });
    await second.promise;
  });
  expect(screen.getByTestId("data")).toHaveTextContent("latest");
  expect(screen.getByTestId("loading")).toHaveTextContent("false");

  await act(async () => {
    first.resolve({ data: "stale" });
    await first.promise;
  });
  expect(screen.getByTestId("data")).toHaveTextContent("latest");
  expect(screen.getByTestId("error")).toHaveTextContent("-");
});
