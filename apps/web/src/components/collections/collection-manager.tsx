"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useState, type FormEvent } from "react";
import {
  createCollectionAction,
  deleteCollectionAction,
  renameCollectionAction,
} from "@/app/collections/actions";
import type { CollectionData } from "@/types/collection";

const inputClassName = "min-h-11 w-full border border-[var(--border-strong)] bg-[var(--surface)] px-3 text-[16px] text-[var(--text)] outline-none focus:border-[var(--accent)] sm:text-[14px]";
const quietButtonClassName = "min-h-11 px-2 text-[13px] font-medium text-[var(--text-muted)] hover:text-[var(--accent-strong)] focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[var(--accent)] disabled:opacity-60";

function CollectionRow({ collection }: { collection: CollectionData }) {
  const router = useRouter();
  const [editing, setEditing] = useState(false);
  const [name, setName] = useState(collection.name);
  const [confirmDelete, setConfirmDelete] = useState(false);
  const [pending, setPending] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const rename = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (pending) return;
    setPending(true);
    setError(null);
    const result = await renameCollectionAction(collection.id, name);
    setPending(false);
    if (!result.ok) {
      setError(result.message);
      return;
    }
    setEditing(false);
    setName(result.collection.name);
    router.refresh();
  };

  const remove = async () => {
    if (!confirmDelete) {
      setConfirmDelete(true);
      return;
    }
    if (pending) return;
    setPending(true);
    setError(null);
    const result = await deleteCollectionAction(collection.id);
    setPending(false);
    if (!result.ok) {
      setError(result.message);
      return;
    }
    router.refresh();
  };

  return (
    <li className="border-b border-[var(--border)] py-4 last:border-b-0">
      {editing ? (
        <form onSubmit={(event) => void rename(event)} aria-busy={pending} className="flex flex-wrap items-center gap-2">
          <label className="sr-only" htmlFor={`rename-collection-${collection.id}`}>Collection name</label>
          <input
            id={`rename-collection-${collection.id}`}
            className={`${inputClassName} max-w-[360px]`}
            name="name"
            maxLength={100}
            required
            autoFocus
            aria-invalid={error ? true : undefined}
            aria-describedby={error ? `collection-error-${collection.id}` : undefined}
            value={name}
            onChange={(event) => setName(event.target.value)}
          />
          <button type="submit" disabled={pending} className="min-h-11 px-3 text-[13px] font-medium text-[var(--accent-strong)] disabled:opacity-60">
            {pending ? "Saving…" : "Save"}
          </button>
          <button type="button" disabled={pending} onClick={() => { setEditing(false); setName(collection.name); setError(null); }} className={quietButtonClassName}>Cancel</button>
        </form>
      ) : (
        <div className="flex flex-wrap items-center justify-between gap-x-6 gap-y-2">
          <div className="min-w-0 flex-1 basis-[200px]">
            <Link href={`/collections/${collection.id}`} className="break-words text-[16px] font-medium text-[var(--text)] underline-offset-4 hover:text-[var(--accent-strong)] hover:underline focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[var(--accent)] [overflow-wrap:anywhere]">
              {collection.name}
            </Link>
            <p className="mt-1 text-[12px] text-[var(--text-subtle)]">{collection.knowledgeCount} {collection.knowledgeCount === 1 ? "note" : "notes"}</p>
          </div>
          <div className="flex items-center gap-2">
            <button type="button" aria-label={`Rename ${collection.name}`} disabled={pending} onClick={() => { setEditing(true); setConfirmDelete(false); setError(null); }} className={quietButtonClassName}>Rename</button>
            <button type="button" aria-label={`${confirmDelete ? "Confirm delete" : "Delete"} ${collection.name}`} aria-describedby={confirmDelete ? `collection-delete-${collection.id}` : undefined} disabled={pending} onClick={() => void remove()} className={`${quietButtonClassName} ${confirmDelete ? "text-[var(--danger)]" : ""}`}>
              {pending ? "Deleting…" : confirmDelete ? "Confirm delete" : "Delete"}
            </button>
            {confirmDelete && <button type="button" disabled={pending} onClick={() => setConfirmDelete(false)} className={quietButtonClassName}>Cancel</button>}
          </div>
        </div>
      )}
      {confirmDelete && !editing && (
        <p id={`collection-delete-${collection.id}`} role="status" className="mt-2 text-[12px] leading-5 text-[var(--danger)]">The {collection.knowledgeCount} {collection.knowledgeCount === 1 ? "note" : "notes"} in this collection will become unfiled. Notes and history remain intact.</p>
      )}
      {error && <p id={`collection-error-${collection.id}`} role="alert" className="mt-2 text-[12px] text-[var(--danger)]">{error}</p>}
    </li>
  );
}

export function CollectionManager({ collections }: { collections: CollectionData[] }) {
  const router = useRouter();
  const [name, setName] = useState("");
  const [creating, setCreating] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const create = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (creating) return;
    setCreating(true);
    setError(null);
    const result = await createCollectionAction(name);
    setCreating(false);
    if (!result.ok) {
      setError(result.message);
      return;
    }
    setName("");
    router.refresh();
  };

  return (
    <div>
      <form onSubmit={(event) => void create(event)} aria-busy={creating} className="border-b border-[var(--border)] pb-8">
        <label htmlFor="new-collection-name" className="mb-2 block text-[12px] font-medium text-[var(--text-subtle)]">New collection</label>
        <div className="flex flex-wrap items-center gap-2">
          <input
            id="new-collection-name"
            name="name"
            className={`${inputClassName} max-w-[360px]`}
            placeholder="Collection name"
            maxLength={100}
            required
            aria-invalid={error ? true : undefined}
            aria-describedby={error ? "new-collection-error" : undefined}
            value={name}
            onChange={(event) => setName(event.target.value)}
          />
          <button type="submit" disabled={creating} className="min-h-11 border border-[var(--accent)] bg-[var(--accent)] px-4 text-[13px] font-medium text-[var(--accent-contrast)] hover:bg-[var(--accent-strong)] disabled:opacity-60">
            {creating ? "Creating…" : "Create collection"}
          </button>
        </div>
        {error && <p id="new-collection-error" role="alert" className="mt-2 text-[12px] text-[var(--danger)]">{error}</p>}
      </form>

      <section className="pt-7" aria-labelledby="collections-heading">
        <div className="flex items-baseline justify-between gap-4 border-b border-[var(--border)] pb-3">
          <h2 id="collections-heading" className="text-[13px] font-medium text-[var(--text-muted)]">Your collections</h2>
          <span className="text-[12px] text-[var(--text-subtle)]">{collections.length} collections</span>
        </div>
        {collections.length > 0 ? (
          <ul>{collections.map((collection) => <CollectionRow key={collection.id} collection={collection} />)}</ul>
        ) : (
          <p className="py-12 text-[14px] text-[var(--text-muted)]">Create a collection to group related notes.</p>
        )}
      </section>
    </div>
  );
}
