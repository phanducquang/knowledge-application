import bash from "highlight.js/lib/languages/bash";
import css from "highlight.js/lib/languages/css";
import dockerfile from "highlight.js/lib/languages/dockerfile";
import gradle from "highlight.js/lib/languages/gradle";
import ini from "highlight.js/lib/languages/ini";
import java from "highlight.js/lib/languages/java";
import javascript from "highlight.js/lib/languages/javascript";
import json from "highlight.js/lib/languages/json";
import kotlin from "highlight.js/lib/languages/kotlin";
import markdown from "highlight.js/lib/languages/markdown";
import nginx from "highlight.js/lib/languages/nginx";
import python from "highlight.js/lib/languages/python";
import sql from "highlight.js/lib/languages/sql";
import typescript from "highlight.js/lib/languages/typescript";
import xml from "highlight.js/lib/languages/xml";
import yaml from "highlight.js/lib/languages/yaml";
import { createLowlight } from "lowlight";

const highlighter = createLowlight({
  bash,
  css,
  dockerfile,
  gradle,
  ini,
  java,
  javascript,
  json,
  kotlin,
  markdown,
  nginx,
  python,
  sql,
  typescript,
  xml,
  yaml,
});

const languageAliases: Readonly<Record<string, string>> = {
  bash: "bash",
  shell: "bash",
  sh: "bash",
  zsh: "bash",
  css: "css",
  docker: "dockerfile",
  dockerfile: "dockerfile",
  gradle: "gradle",
  html: "xml",
  ini: "ini",
  java: "java",
  javascript: "javascript",
  js: "javascript",
  jsx: "javascript",
  json: "json",
  jsonc: "json",
  kotlin: "kotlin",
  kt: "kotlin",
  markdown: "markdown",
  md: "markdown",
  nginx: "nginx",
  properties: "ini",
  python: "python",
  py: "python",
  sql: "sql",
  typescript: "typescript",
  ts: "typescript",
  tsx: "typescript",
  xml: "xml",
  xhtml: "xml",
  yaml: "yaml",
  yml: "yaml",
};

export function normalizeCodeLanguage(language: string | null | undefined) {
  if (!language) return null;
  return languageAliases[language.trim().toLowerCase()] ?? null;
}

export function codeLanguageFromClassName(className: string | undefined) {
  const language = className?.match(/(?:^|\s)language-([^\s]+)/)?.[1];
  return normalizeCodeLanguage(language);
}

export function highlightCode(source: string, className: string | undefined) {
  const language = codeLanguageFromClassName(className);
  if (!language) return null;
  return highlighter.highlight(language, source);
}

export function highlightedSourceText(tree: ReturnType<typeof highlighter.highlight>) {
  const text = (nodes: typeof tree.children): string =>
    nodes
      .map((node) => {
        if (node.type === "text") return node.value;
        if (node.type === "element") return text(node.children);
        return "";
      })
      .join("");
  return text(tree.children);
}
