import type { Link, Root, Text } from "mdast";
import { visit } from "unist-util-visit";

export interface WikiTarget {
  slug: string;
  title: string;
}

const WIKI_REFERENCE = /\[\[([a-z0-9]+(?:-[a-z0-9]+)*)\]\]/g;

function isEscaped(source: string, index: number) {
  let slashes = 0;
  for (let cursor = index - 1; cursor >= 0 && source[cursor] === "\\"; cursor--) {
    slashes++;
  }
  return slashes % 2 === 1;
}

function splitWikiText(
  node: Text,
  source: string,
  titles: ReadonlyMap<string, string>,
): Array<Text | Link> {
  const start = node.position?.start.offset;
  const end = node.position?.end.offset;
  if (start === undefined || end === undefined) return [node];
  const raw = source.slice(start, end);
  const rawMatches = Array.from(raw.matchAll(WIKI_REFERENCE));
  const pieces: Array<Text | Link> = [];
  let cursor = 0;
  let matchIndex = 0;

  for (const match of node.value.matchAll(WIKI_REFERENCE)) {
    const rawMatch = rawMatches[matchIndex++];
    const slug = match[1];
    const title = titles.get(slug);
    if (!title || !rawMatch || rawMatch[1] !== slug || isEscaped(raw, rawMatch.index)) {
      continue;
    }
    if (match.index > cursor) {
      pieces.push({ type: "text", value: node.value.slice(cursor, match.index) });
    }
    pieces.push({
      type: "link",
      url: `/knowledge/${slug}`,
      title: null,
      children: [{ type: "text", value: title }],
    });
    cursor = match.index + match[0].length;
  }
  if (pieces.length === 0) return [node];
  if (cursor < node.value.length) {
    pieces.push({ type: "text", value: node.value.slice(cursor) });
  }
  return pieces;
}

/** Remark transformer enabled only for authenticated owner reading. */
export function remarkWikiLinks(options: { source: string; targets: WikiTarget[] }) {
  const titles = new Map(options.targets.map(({ slug, title }) => [slug, title]));
  return (tree: Root) => {
    visit(tree, "text", (node, index, parent) => {
      if (index === undefined || !parent || parent.type === "link" || parent.type === "linkReference") {
        return;
      }
      const pieces = splitWikiText(node, options.source, titles);
      if (pieces.length === 1 && pieces[0] === node) return;
      (parent.children as Array<Text | Link>).splice(index, 1, ...pieces);
      return index + pieces.length;
    });
  };
}
