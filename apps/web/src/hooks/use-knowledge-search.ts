"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import {
  fetchKnowledgeSearch,
  LatestSearchRequest,
  SEARCH_DEBOUNCE_MS,
} from "@/lib/knowledge-search";
import type { KnowledgeListItemData } from "@/types/knowledge";

type SearchStatus = "idle" | "searching" | "success" | "error";

interface SearchState {
  query: string;
  results: KnowledgeListItemData[];
  status: SearchStatus;
}

interface InitialSearchState {
  query?: string;
  results?: KnowledgeListItemData[];
  failed?: boolean;
}

export function useKnowledgeSearch(limit: number, initial: InitialSearchState = {}) {
  const timerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const abortRef = useRef<AbortController | null>(null);
  const requestsRef = useRef(new LatestSearchRequest());
  const initialQuery = initial.query?.trim() ?? "";
  const [state, setState] = useState<SearchState>({
    query: initialQuery,
    results: initial.results ?? [],
    status: initialQuery ? (initial.failed ? "error" : "success") : "idle",
  });

  const cancelPending = useCallback(() => {
    if (timerRef.current) {
      clearTimeout(timerRef.current);
      timerRef.current = null;
    }
    abortRef.current?.abort();
    abortRef.current = null;
    requestsRef.current.invalidate();
  }, []);

  useEffect(() => cancelPending, [cancelPending]);

  const search = useCallback(
    (rawQuery: string) => {
      cancelPending();
      const query = rawQuery.trim();

      if (!query) {
        setState({ query: "", results: [], status: "idle" });
        return;
      }

      setState({ query, results: [], status: "searching" });
      timerRef.current = setTimeout(async () => {
        timerRef.current = null;
        const requestSequence = requestsRef.current.begin();
        const controller = new AbortController();
        abortRef.current = controller;

        try {
          const results = await fetchKnowledgeSearch(query, limit, controller.signal);
          if (requestsRef.current.isLatest(requestSequence)) {
            setState({ query, results, status: "success" });
          }
        } catch (error) {
          if (error instanceof DOMException && error.name === "AbortError") {
            return;
          }
          if (requestsRef.current.isLatest(requestSequence)) {
            setState({ query, results: [], status: "error" });
          }
        } finally {
          if (requestsRef.current.isLatest(requestSequence)) {
            abortRef.current = null;
          }
        }
      }, SEARCH_DEBOUNCE_MS);
    },
    [cancelPending, limit],
  );

  return { ...state, search };
}
