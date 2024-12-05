import pandas as pd

df_with_version = pd.read_csv("/Users/nabhansuwanachote/Desktop/research/msr-2025-challenge/java-dependency-analyzer/analyzer-python/data_with_date/success-project-with-version.csv")
df_with_version

success_project_df = pd.read_csv("/Users/nabhansuwanachote/Desktop/research/msr-2025-challenge/java-dependency-analyzer/new-dependency-output/success_project.csv")
success_project_list = success_project_df["artifact_id"].tolist()

df_with_version["tags"] = None
df_with_version.fillna(value="null", inplace=True)
df_with_version

import requests
import time
import json
import os


class GitHubTagFetcher:
    def __init__(self, cache_file="cache-2.json"):
        """
        Initializes the GitHubTagFetcher class, loading cache from file if available.

        Parameters:
            cache_file (str): Path to the cache file for storing fetched data.
        """
        self.cache_file = cache_file
        self.cache = self._load_cache()

    def _load_cache(self):
        """
        Loads the cache from the cache file if it exists.

        Returns:
            dict: The loaded cache, or an empty dictionary if the file does not exist.
        """
        if os.path.exists(self.cache_file):
            with open(self.cache_file, "r") as file:
                try:
                    return json.load(file)
                except json.JSONDecodeError:
                    print("Cache file is invalid. Starting with an empty cache.")
                    return {}
        return {}

    def _save_cache(self):
        """
        Saves the current cache to the cache file.
        """
        with open("cache-2.json", "w") as file:
            json.dump(self.cache, file, indent=4)

    def get_all_tags(self, owner, repo, token):
        """
        Fetch all tags for a given GitHub repository and sort them by commit date.

        Parameters:
            owner (str): The owner of the repository.
            repo (str): The repository name.
            token (TokenList): GitHub personal access token for authentication.

        Returns:
            list: A list of tags sorted by commit date, with each tag containing its name and commit date.
        """
        cache_key = f"{owner}/{repo}"
        if cache_key in self.cache:
            print(f"Using cached data for {cache_key}")
            return self.cache[cache_key]

        BASE_URL = "https://api.github.com/repos"
        url = f"{BASE_URL}/{owner}/{repo}/tags"
        headers = {"Accept": "application/vnd.github+json"}
        if token:
            headers["Authorization"] = f"token {token.get_token()}"

        tags_with_dates = []
        while url:
            headers["Authorization"] = f"token {token.get_token()}"
            response = requests.get(url, headers=headers)
            if response.status_code == 200:
                tags = response.json()
                for tag in tags:
                    commit_url = tag["commit"]["url"]
                    commit_response = requests.get(commit_url, headers=headers)
                    print("Calling commit api")
                    if commit_response.status_code == 200:
                        commit_data = commit_response.json()
                        commit_date = commit_data["commit"]["committer"]["date"]
                        tags_with_dates.append({"name": tag["name"], "date": commit_date})
                print("Calling . . .")
                url = response.links.get("next", {}).get("url")
            elif response.status_code == 403:
                token.switch_token()
                print(response.json())
                time.sleep(5)
                continue
            else:
                print(f"Failed to fetch tags: {response.status_code}")
                print(response.json())  # Print error details
                break
            time.sleep(0.1)

        # Sort tags by commit date
        tags_with_dates.sort(key=lambda x: x["date"])

        # Cache the result
        self.cache[cache_key] = tags_with_dates
        self._save_cache()
        time.sleep(1)
        return tags_with_dates
#%%
class TokenList:

  def __init__(self):
    token = "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"
    token2 = "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"
    self.token_index = 1
    self.token_list = ["",token, token2]

  def switch_token(self):
    self.token_index = self.token_index * -1
    print("Switching token to: " + self.get_token())

  def get_token(self):
    return self.token_list[self.token_index]

# %%
token = TokenList()
tag = GitHubTagFetcher()
current_index = 1
# df_with_version["tags"] = None

for index, row in df_with_version.iterrows():
    print(current_index)
    if row["artifact_id"] not in success_project_list:
        continue
    current_index = current_index + 1
    if current_index % 50 == 0:
        print("Saving to file")
        df_with_version.to_csv(f"data_with_date/java-sampling-with-pom-exist-version-tag-{current_index}.csv")
    if row["tags"] != "null":
        continue
    if row["project_owner"] == "eclipse-platform":
        print("Pass eclipse platform")
        continue
    print("Running on project: " + row["artifact_id"])
    tags = tag.get_all_tags(row["project_owner"], row["project_name"], token)
    df_with_version.at[index, "tags"] = tags
    time.sleep(0.05)

df_with_version.to_csv("data_with_date/success-project-with-version-tag.csv", index=False)