/** Remove espaços e caracteres de controle (U+0000 a U+0020) das pontas, em tempo linear (sem regex). */
export function trimEdges(value: string): string {
  const isEdge = (char: string) => char <= ' ' || char.trim() === '';
  let start = 0;
  let end = value.length;
  while (start < end && isEdge(value[start])) start++;
  while (end > start && isEdge(value[end - 1])) end--;
  return value.slice(start, end);
}
