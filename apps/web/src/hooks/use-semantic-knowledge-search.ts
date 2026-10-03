"use client";
import { useEffect, useState, useSyncExternalStore } from "react";
import { SemanticSearchSession, type SemanticState } from "@/lib/knowledge-semantic-search";

export function useSemanticKnowledgeSearch(initial: Partial<SemanticState>) {
  const [session] = useState(() => new SemanticSearchSession(initial));
  const state = useSyncExternalStore(session.subscribe, session.getSnapshot, session.getSnapshot);
  useEffect(() => () => session.cancel(), [session]);
  return { ...state, session };
}
