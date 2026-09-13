export function isMermaidCodeBlock(className: string | undefined) {
  const language = className?.match(/(?:^|\s)language-([^\s]+)/)?.[1];
  return language?.toLowerCase() === "mermaid";
}

export function codeBlockSourceText(children: readonly unknown[]) {
  const text = (value: unknown): string => {
    if (typeof value === "string" || typeof value === "number") {
      return String(value);
    }
    if (Array.isArray(value)) {
      return value.map(text).join("");
    }
    return "";
  };

  return children.map(text).join("");
}

export function createMermaidRenderGuard() {
  let currentAttempt = 0;

  return {
    begin() {
      currentAttempt += 1;
      return currentAttempt;
    },
    isCurrent(attempt: number) {
      return attempt === currentAttempt;
    },
    invalidate() {
      currentAttempt += 1;
    },
  };
}

export function mermaidRenderId(reactId: string, attempt: number) {
  const safeReactId = reactId.replace(/[^a-zA-Z0-9_-]/g, "") || "diagram";
  return `knowledge-mermaid-${safeReactId}-${attempt}`;
}
