import { useMemo, useState } from 'react';

export interface PaginatedSlice<T> {
  currentPage: number;
  totalPages: number;
  pagedItems: T[];
  setCurrentPage: (page: number | ((prev: number) => number)) => void;
  prevPage: () => void;
  nextPage: () => void;
  resetPage: () => void;
}

/**
 * Deep pagination hook: encapsulates page state, automatic bounds clamping
 * when the underlying collection shrinks, and slice calculation.
 */
export function usePaginatedList<T>(items: readonly T[], pageSize: number): PaginatedSlice<T> {
  const [rawPage, setCurrentPage] = useState(1);
  const totalPages = Math.max(1, Math.ceil(items.length / pageSize));
  const currentPage = Math.min(Math.max(1, rawPage), totalPages);

  const pagedItems = useMemo(() => {
    const start = (currentPage - 1) * pageSize;
    return items.slice(start, start + pageSize);
  }, [items, currentPage, pageSize]);

  const prevPage = () => setCurrentPage((p) => Math.max(1, Math.min(p, totalPages) - 1));
  const nextPage = () => setCurrentPage((p) => Math.min(totalPages, Math.min(p, totalPages) + 1));
  const resetPage = () => setCurrentPage(1);

  return {
    currentPage,
    totalPages,
    pagedItems,
    setCurrentPage,
    prevPage,
    nextPage,
    resetPage,
  };
}
