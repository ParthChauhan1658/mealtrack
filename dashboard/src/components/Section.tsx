import type { ReactNode } from "react";
import type { Loadable } from "../useLoad";

/** Card with a title plus consistent loading and error states. */
export function Section<T>({
  title,
  state,
  children,
  aside,
}: {
  title: string;
  state: Loadable<T>;
  children: (data: T) => ReactNode;
  aside?: ReactNode;
}) {
  return (
    <section className="card">
      <header className="card-head">
        <h2>{title}</h2>
        {aside}
      </header>
      {state.error ? (
        <div className="error" role="alert">
          {state.error} <button onClick={state.reload}>Retry</button>
        </div>
      ) : state.data === null ? (
        <div className="muted" aria-busy="true">Loading…</div>
      ) : (
        <div className={state.loading ? "dim" : undefined}>{children(state.data)}</div>
      )}
    </section>
  );
}
