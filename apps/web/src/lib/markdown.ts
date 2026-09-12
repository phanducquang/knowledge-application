export interface MarkdownHeading {
  id: string;
  label: string;
  level: 2 | 3 | 4;
}

export function headingSlugBase(label: string) {
  const slug = label
    .normalize("NFKD")
    .replace(/[\u0300-\u036f]/g, "")
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, "-")
    .replace(/^-+|-+$/g, "");

  return slug || "section";
}

export function createHeadingSlugger() {
  const occurrences = new Map<string, number>();

  return (label: string) => {
    const base = headingSlugBase(label);
    const occurrence = occurrences.get(base) ?? 0;
    occurrences.set(base, occurrence + 1);
    return occurrence === 0 ? base : `${base}-${occurrence + 1}`;
  };
}

function plainHeadingLabel(markdown: string) {
  return markdown
    .replace(/!\[([^\]]*)\]\([^)]*\)/g, "$1")
    .replace(/\[([^\]]+)\]\([^)]*\)/g, "$1")
    .replace(/`([^`]*)`/g, "$1")
    .replace(/[~*_]/g, "")
    .trim();
}

export function extractMarkdownHeadings(markdown: string): MarkdownHeading[] {
  const headings: MarkdownHeading[] = [];
  const nextSlug = createHeadingSlugger();
  let fence: { marker: "`" | "~"; length: number } | null = null;

  for (const line of markdown.split(/\r?\n/)) {
    const fenceMatch = line.match(/^ {0,3}(`{3,}|~{3,})/);
    if (fenceMatch) {
      const markerText = fenceMatch[1];
      const marker = markerText[0] as "`" | "~";
      if (!fence) {
        fence = { marker, length: markerText.length };
      } else if (fence.marker === marker && markerText.length >= fence.length) {
        fence = null;
      }
      continue;
    }

    if (fence) {
      continue;
    }

    const headingMatch = line.match(/^ {0,3}(#{2,4})\s+(.+?)\s*#*\s*$/);
    if (!headingMatch) {
      continue;
    }

    const label = plainHeadingLabel(headingMatch[2]);
    if (!label) {
      continue;
    }

    headings.push({
      id: nextSlug(label),
      label,
      level: headingMatch[1].length as 2 | 3 | 4,
    });
  }

  return headings;
}

export function calculateReadTime(markdown: string) {
  const words = markdown.match(/[\p{L}\p{N}]+(?:['’-][\p{L}\p{N}]+)*/gu)?.length ?? 0;
  return `${Math.max(1, Math.ceil(words / 200))} min read`;
}
