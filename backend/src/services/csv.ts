/** Quoted CSV still allows spreadsheet formulas. Remove controls and neutralize formula prefixes. */
export function csvCell(value: string): string {
  const clean = value.replace(/[\u0000-\u001f\u007f-\u009f]/g, '');
  return '"' + (/^\s*[=+@-]/u.test(clean) ? "'" : '') + clean.replace(/"/g, '""') + '"';
}
