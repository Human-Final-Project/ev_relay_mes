import fs from "fs";
import path from "path";

const sourceRoot = path.resolve(__dirname, "..");

function listJavaScriptFiles(directory) {
  return fs.readdirSync(directory, { withFileTypes: true }).flatMap((entry) => {
    const fullPath = path.join(directory, entry.name);
    if (entry.isDirectory()) {
      return listJavaScriptFiles(fullPath);
    }
    return entry.isFile() && entry.name.endsWith(".js") ? [fullPath] : [];
  });
}

function resolveLocalModule(importer, request) {
  const basePath = path.resolve(path.dirname(importer), request);
  return [
    basePath,
    `${basePath}.js`,
    path.join(basePath, "index.js"),
  ].find((candidate) => fs.existsSync(candidate) && fs.statSync(candidate).isFile());
}

function importedModules(filePath) {
  const source = fs.readFileSync(filePath, "utf8");
  const requests = [];
  const patterns = [
    /import\s+(?:[^"'`;]+?\s+from\s+)?["'](\.[^"']+)["']/g,
    /require\s*\(\s*["'](\.[^"']+)["']\s*\)/g,
  ];

  patterns.forEach((pattern) => {
    let match;
    while ((match = pattern.exec(source)) !== null) {
      requests.push(match[1]);
    }
  });

  return requests
    .map((request) => resolveLocalModule(filePath, request))
    .filter(Boolean);
}

test("all production JavaScript modules are reachable from index.js", () => {
  const reachable = new Set();
  const pending = [path.join(sourceRoot, "index.js")];

  while (pending.length > 0) {
    const current = pending.pop();
    if (reachable.has(current)) {
      continue;
    }
    reachable.add(current);
    importedModules(current).forEach((dependency) => pending.push(dependency));
  }

  const unreachable = listJavaScriptFiles(sourceRoot)
    .filter((filePath) => !filePath.endsWith(".test.js"))
    .filter((filePath) => path.basename(filePath) !== "setupTests.js")
    .filter((filePath) => !reachable.has(filePath))
    .map((filePath) => path.relative(sourceRoot, filePath).replaceAll("\\", "/"));

  expect(unreachable).toEqual([]);
});
