import json

data = []
with open("pr_comments.json") as f:
    for line in f:
        line = line.strip()
        if line:
            data.append(json.loads(line))

for pr in data:
    print(f"PR #{pr.get('number')}: {pr.get('title')}")
    for c in pr.get("comments", []):
        author = c.get("author", {}).get("login", "Unknown") if c.get("author") else "Unknown"
        body = c.get("body", "").replace("\n", " ")[:150]
        print(f"  - Comment by {author}: {body}...")
    for r in pr.get("reviews", []):
        author = r.get("author", {}).get("login", "Unknown") if r.get("author") else "Unknown"
        body = r.get("body", "").replace("\n", " ")[:150]
        print(f"  - Review by {author}: {body}...")
