import { execSync } from "child_process";
import * as matter from "gray-matter";
import * as fs from "fs";
import * as path from "path";

// Note: this must be executed within the docs-website directory.

// Constants.
const GITHUB_BROWSE_URL = "https://github.com/linkedin/datahub/blob/master";

function list_markdown_files(): string[] {
  const all_markdown_files = execSync("cd .. && git ls-files . | grep '.md$'")
    .toString()
    .trim()
    .split("\n");

  const filter_patterns = [
    // We don't need our issue and pull request templates.
    /^\.github\//,
    // Ignore everything within this directory.
    /^docs-website\//,
    // Don't want hosted docs for these.
    /^contrib\//,
    // /^contrib\/(?!kubernetes)/, // Keeps the Kubernetes docs.
    /^datahub-web\//,
    /^metadata-ingestion-examples\//,
    /^docs\/rfc\/templates\/000-template\.md$/,
    /^docs\/docker\/README\.md/, // This one is just a pointer to another file.
  ];

  const markdown_files = all_markdown_files.filter((filepath) => {
    return !filter_patterns.some((rule) => rule.test(filepath));
  });
  return markdown_files;
}

const markdown_files = list_markdown_files();
// console.log(markdown_files);

function get_id(filepath: string): string {
  // Removes the file extension (e.g. md).
  const id = filepath.replace(/\.[^/.]+$/, "");
  // console.log(id);
  return id;
}

const hardcoded_slugs = {
  "README.md": "/",
  "docs/README.md": "docs/overview",
};

function get_slug(filepath: string): string {
  if (filepath in hardcoded_slugs) {
    return hardcoded_slugs[filepath];
  }

  let slug = get_id(filepath);
  if (slug.startsWith("docs/")) {
    slug = slug.slice(5);
  }
  slug = `/${slug}`;
  if (slug.endsWith("/README")) {
    slug = slug.slice(0, -7);
  }
  slug = slug.toLowerCase();
  return slug;
}

const hardcoded_titles = {
  "README.md": "Introduction",
};

function markdown_guess_title(
  contents: matter.GrayMatterFile<string>,
  filepath: string
): void {
  if (contents.data.title) {
    return;
  }

  let title: string;
  if (filepath in hardcoded_titles) {
    title = hardcoded_titles[filepath];
  } else {
    // Find first h1 header and use it as the title.
    // TODO: check if there are multiple h1 headers and warn if so
    const header = contents.content.match(/^# (.+)$/m);
    title = header[1].trim();
    if (title.startsWith("DataHub ")) {
      title = title.slice(8).trim();
    }
  }

  contents.data.title = title;
  contents.data.hide_title = true;
}

function markdown_add_slug(
  contents: matter.GrayMatterFile<string>,
  filepath: string
): void {
  if (contents.data.slug) {
    return;
  }

  const slug = get_slug(filepath);
  contents.data.slug = slug;
}

function new_url(original: string, filepath: string): string {
  if (original.startsWith("http://") || original.startsWith("https://")) {
    // TODO warn on absolute internal github links
    return original;
  }

  if (original.startsWith("#")) {
    // These are anchor links that reference within the document itself.
    return original;
  }

  // Now we assume this is a local reference.
  const suffix = path.extname(original);
  if (
    suffix == "" ||
    [
      ".java",
      ".conf",
      ".xml",
      ".pdl",
      ".json",
      ".py",
      ".ts",
      ".yml",
      ".sh",
    ].some((ext) => suffix.startsWith(ext))
  ) {
    // A reference to a file or directory in the Github repo.
    const relation = path.dirname(filepath);
    const updated = path.normalize(
      `${GITHUB_BROWSE_URL}/${relation}/${original}`
    );
    return updated;
  } else if (suffix.startsWith(".md")) {
    // Leave as-is.
    // We use startsWith here so that we can allow anchor tags on links.
    return original;
  } else if ([".png", ".svg", ".pdf"].includes(suffix)) {
    // Let docusaurus bundle these as static assets.
    const up_levels = (filepath.match(/\//g) ?? []).length;
    const relation = path.dirname(filepath);
    const updated = path.normalize(
      `${"../".repeat(up_levels + 2)}/${relation}/${original}`
    );
    return updated;
  } else {
    throw `unknown extension - ${original} in ${filepath}`;
  }
}

function markdown_rewrite_urls(
  contents: matter.GrayMatterFile<string>,
  filepath: string
): void {
  const new_content = contents.content
    .replace(
      // Look for the [text](url) syntax. Note that this will also capture images.
      //
      // We do a little bit of parenthesis matching here to account for parens in URLs.
      // See https://stackoverflow.com/a/17759264 for explanation of the second capture group.
      /\[(.+?)\]\(((?:[^)(]+|\((?:[^)(]+|\([^)(]*\))*\))*)\)/g,
      (_, text, url) => {
        const updated = new_url(url.trim(), filepath);
        return `[${text}](${updated})`;
      }
    )
    .replace(
      // Also look for the [text]: url syntax.
      /^\[(.+?)\]\s*:\s*(.+?)\s*$/gm,
      (_, text, url) => {
        const updated = new_url(url, filepath);
        return `[${text}]: ${updated}`;
      }
    );
  contents.content = new_content;
}

for (const filepath of markdown_files) {
  // console.log("Processing:", filepath);
  const contents_string = fs.readFileSync(`../${filepath}`).toString();
  const contents = matter(contents_string);

  markdown_guess_title(contents, filepath);
  markdown_add_slug(contents, filepath);
  markdown_rewrite_urls(contents, filepath);
  // console.log(contents);

  const outpath = `genDocs/${filepath}`;
  const pathname = path.dirname(outpath);
  fs.mkdirSync(pathname, { recursive: true });
  fs.writeFileSync(outpath, contents.stringify(""));
}

// Output a list of all docs which are not included in a sidebar.
const sidebar = require("./sidebars.js");
const sidebar_json = JSON.stringify(sidebar);
for (const filepath of markdown_files) {
  const doc_id = get_id(filepath);

  if (sidebar_json.indexOf(`"${doc_id}"`) < 0) {
    console.warn("Not included in sidebar:", filepath);
  }
}
