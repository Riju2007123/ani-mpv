/* ANI MPV Static Website - App Logic */

const ANILIST_API = "https://graphql.anilist.co";
const HIANIME_BASE = "https://hianime.to";
const CORS_PROXY = "https://api.allorigins.win/raw?url="; // public CORS proxy for file://

// localStorage keys
const STORAGE_WATCHLIST = "ani_mpv_watchlist";
const STORAGE_HISTORY = "ani_mpv_history";
const STORAGE_DOWNLOADS = "ani_mpv_downloads";

let hlsInstance = null;
let currentPlaying = null;
let watchlistBtnEl = null;
let downloadBtnEl = null;

/* ---------- Storage Helpers ---------- */
function getStorage(key, def = []) {
    try { return JSON.parse(localStorage.getItem(key) || JSON.stringify(def)); } catch { return def; }
}
function setStorage(key, val) { try { localStorage.setItem(key, JSON.stringify(val)); } catch {} }

/* ---------- Fetch with CORS Proxy ---------- */
async function fetchJson(url) {
    const proxied = CORS_PROXY + encodeURIComponent(url);
    const res = await fetch(proxied, { headers: { "Accept": "application/json" } });
    if (!res.ok) throw new Error("HTTP " + res.status);
    return res.json();
}

async function fetchText(url) {
    const proxied = CORS_PROXY + encodeURIComponent(url);
    const res = await fetch(proxied);
    if (!res.ok) throw new Error("HTTP " + res.status);
    return res.text();
}

/* ---------- AniList API ---------- */
async function anilistQuery(query, variables = {}) {
    const res = await fetch(ANILIST_API, {
        method: "POST",
        headers: { "Content-Type": "application/json", "Accept": "application/json" },
        body: JSON.stringify({ query, variables }),
    });
    const json = await res.json();
    if (json.errors) throw new Error(json.errors.map(e => e.message).join(", "));
    return json.data;
}

/* ---------- HiAnime Stream Resolution ---------- */
async function resolveHianimeStream(animeId) {
    // HiAnime episode list (public AJAX)
    const epRes = await fetch(`${HIANIME_BASE}/ajax/v2/episode/list/${animeId}`);
    const epJson = await epRes.json();
    if (!epJson || !epJson.episodes || epJson.episodes.length === 0) throw new Error("No episodes");

    const episode = epJson.episodes[0];
    const episodeId = episode.id;

    const srvRes = await fetch(`${HIANIME_BASE}/ajax/v2/episode/servers?episodeId=${episodeId}`);
    const srvJson = await srvRes.json();
    if (!srvJson || !srvJson.servers || srvJson.servers.length === 0) throw new Error("No servers");

    for (const server of srvJson.servers) {
        if (server.server_data) {
            for (const link of server.server_data) {
                if (link.link) return link.link;
            }
        }
    }
    throw new Error("No stream link");
}

/* ---------- Fallback Mock Data ---------- */
const MOCK_ANIME = [
    { id: "mock-bleach", title: "Bleach: Thousand-Year Blood War", cover: "https://s4.anilist.co/file/anilistcdn/media/anime/cover/large/bx114446-R19y4dY6gWwW.jpg", stream: null },
    { id: "mock-naruto", title: "Naruto Shippuden", cover: "https://s4.anilist.co/file/anilistcdn/media/anime/cover/large/bx20-yJvL3Bjw38Bf.jpg", stream: null },
    { id: "mock-onepiece", title: "One Piece", cover: "https://s4.anilist.co/file/anilistcdn/media/anime/cover/large/bx21-P8KzBst2p1z7.jpg", stream: null },
    { id: "mock-demon", title: "Demon Slayer: Kimetsu no Yaiba", cover: "https://s4.anilist.co/file/anilistcdn/media/anime/cover/large/bx16085-K2c2H8f5vE.jpg", stream: null },
    { id: "mock-attack", title: "Attack on Titan", cover: "https://s4.anilist.co/file/anilistcdn/media/anime/cover/large/bx8677-p3L8tFk6M.jpg", stream: null },
];

/* ---------- Rendering ---------- */
function renderAnimeCards(containerId, list, onCardClick) {
    const grid = document.getElementById(containerId);
    if (!grid) return;
    grid.innerHTML = "";
    if (!list || list.length === 0) {
        grid.innerHTML = `<p class="empty-hint">No results.</p>`;
        return;
    }
    list.forEach(item => {
        const isBookmarked = isWatchlisted(item.id);
        const card = document.createElement("div");
        card.className = "manga-card anime-card";
        card.innerHTML = `
            <img src="${item.cover || 'https://via.placeholder.com/150x220?text=No+Cover'}" alt="${item.title}" loading="lazy">
            <div class="manga-card-info">
                <div class="manga-card-title">${item.title}</div>
                <div class="card-actions">
                    <button class="icon-btn bookmark-btn ${isBookmarked ? 'filled' : ''}" data-id="${item.id}" data-title="${item.title}" data-cover="${item.cover || ''}" title="${isBookmarked ? 'Remove from watchlist' : 'Add to watchlist'}">
                        <i class="fa-solid fa-${isBookmarked ? 'bookmark' : 'bookmark'}"></i>
                    </button>
                </div>
            </div>
        `;
        const img = card.querySelector("img");
        const bookmarkBtn = card.querySelector(".bookmark-btn");

        img.addEventListener("click", (e) => { e.stopPropagation(); onCardClick(item); });
        bookmarkBtn.addEventListener("click", (e) => {
            e.stopPropagation();
            toggleWatchlist(item.id, item.title, item.cover);
        });

        grid.appendChild(card);
    });
}

function renderWatchlist() {
    const grid = document.getElementById("watchlistGrid");
    const list = getStorage(STORAGE_WATCHLIST);
    if (!grid) return;
    grid.innerHTML = "";
    if (list.length === 0) {
        grid.innerHTML = `<p class="empty-hint">Your watchlist is empty. Bookmark anime from the Anime tab.</p>`;
        return;
    }
    renderAnimeCards("watchlistGrid", list, (item) => resolveAndPlay(item));
}

function renderHistory() {
    const grid = document.getElementById("historyGrid");
    const list = getStorage(STORAGE_HISTORY);
    if (!grid) return;
    grid.innerHTML = "";
    if (list.length === 0) {
        grid.innerHTML = `<p class="empty-hint">Your watch history will appear here.</p>`;
        return;
    }
    const historyCards = list.map(h => ({ id: h.id || h.title, title: h.title, cover: h.cover }));
    renderAnimeCards("historyGrid", historyCards, (item) => resolveAndPlay(item));
}

function renderDownloads() {
    const grid = document.getElementById("downloadsGrid");
    const list = getStorage(STORAGE_DOWNLOADS);
    if (!grid) return;
    grid.innerHTML = "";
    if (list.length === 0) {
        grid.innerHTML = `<p class="empty-hint">No downloads yet. Use the Android app to download episodes.</p>`;
        return;
    }
    renderAnimeCards("downloadsGrid", list, (item) => resolveAndPlay(item));
}

/* ---------- Watchlist ---------- */
function isWatchlisted(id) {
    return getStorage(STORAGE_WATCHLIST).some(i => i.id == id);
}

function toggleWatchlist(id, title, cover) {
    const list = getStorage(STORAGE_WATCHLIST);
    const existing = list.findIndex(i => i.id == id);
    if (existing >= 0) {
        list.splice(existing, 1);
        if (watchlistBtnEl) watchlistBtnEl.classList.remove("filled");
    } else {
        list.push({ id, title, cover: cover || "", addedAt: Date.now() });
        if (watchlistBtnEl) watchlistBtnEl.classList.add("filled");
    }
    setStorage(STORAGE_WATCHLIST, list);
    renderWatchlist();
}

/* ---------- History ---------- */
function addHistory(id, title, cover) {
    const list = getStorage(STORAGE_HISTORY);
    const existing = list.findIndex(i => i.id == id);
    if (existing >= 0) {
        list.splice(existing, 1);
    }
    list.unshift({ id, title, cover: cover || "", watchedAt: Date.now() });
    setStorage(STORAGE_HISTORY, list.slice(0, 100));
    renderHistory();
}

/* ---------- Player ---------- */
function destroyHls() {
    if (hlsInstance) {
        hlsInstance.destroy();
        hlsInstance = null;
    }
}

function playWebStream(title, url, id, cover) {
    document.getElementById("playerTitle").innerText = title;
    const video = document.getElementById("videoPlayer");
    const modal = document.getElementById("playerModal");
    const info = document.getElementById("playerInfo");

    destroyHls();
    video.pause();
    video.src = "";
    modal.style.display = "flex";

    addHistory(id, title, cover);

    info.innerHTML = `<i class="fa-solid fa-circle-notch fa-spin"></i> Loading stream...`;
    info.style.display = "block";

    try {
        if (window.Hls && Hls.isSupported() && url && url.endsWith(".m3u8")) {
            hlsInstance = new Hls();
            hlsInstance.loadSource(url);
            hlsInstance.attachMedia(video);
            hlsInstance.on(Hls.Events.MANIFEST_PARSED, () => {
                info.style.display = "none";
                video.play().catch(() => {});
            });
            hlsInstance.on(Hls.Events.ERROR, () => {
                info.innerHTML = `<i class="fa-solid fa-triangle-exclamation" style="color:var(--danger);"></i> Stream error. Try another source.`;
            });
        } else if (url) {
            video.src = url;
            video.onloadedmetadata = () => { info.style.display = "none"; video.play().catch(() => {}); };
            video.onerror = () => {
                info.innerHTML = `<i class="fa-solid fa-triangle-exclamation" style="color:var(--danger);"></i> Cannot play this video format.`;
            };
        } else {
            info.innerHTML = `<i class="fa-solid fa-triangle-exclamation" style="color:var(--danger);"></i> No stream available for this title.`;
        }
    } catch (e) {
        info.innerHTML = `<i class="fa-solid fa-triangle-exclamation" style="color:var(--danger);"></i> ${e.message}`;
    }

    document.getElementById("closePlayer").onclick = () => {
        modal.style.display = "none";
        video.pause();
        video.src = "";
        destroyHls();
        info.style.display = "none";
        watchlistBtnEl = null;
        downloadBtnEl = null;
    };
}

async function resolveAndPlay(item) {
    watchlistBtnEl = document.querySelector(`button.bookmark-btn[data-id="${item.id}"]`);
    const originalHtml = watchlistBtnEl ? watchlistBtnEl.innerHTML : "";
    if (watchlistBtnEl) watchlistBtnEl.innerHTML = `<i class="fa-solid fa-circle-notch fa-spin"></i>`;

    downloadBtnEl = document.getElementById("btnDownload");
    downloadBtnEl.style.display = "none";

    let streamUrl = null;
    try {
        streamUrl = await resolveHianimeStream(item.id);
    } catch (e) {
        console.error("Resolve error:", e);
        streamUrl = null;
    }

    if (watchlistBtnEl) watchlistBtnEl.innerHTML = originalHtml;
    if (watchlistBtnEl) watchlistBtnEl.classList.toggle("filled", isWatchlisted(item.id));

    if (streamUrl) {
        playWebStream(item.title, streamUrl, item.id, item.cover);
        downloadBtnEl.style.display = "flex";
        downloadBtnEl.onclick = () => {
            alert("Download to device is available in the Android app. Streaming started here instead.");
        };
    } else {
        // Fallback: try sample video or show error
        playWebStream(item.title, "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4", item.id, item.cover);
    }
}

/* ---------- Init ---------- */
document.addEventListener("DOMContentLoaded", () => {
    /* Tab Navigation */
    document.querySelectorAll(".tab-btn").forEach(btn => {
        btn.addEventListener("click", () => {
            document.querySelectorAll(".tab-btn").forEach(b => b.classList.remove("active"));
            document.querySelectorAll(".tab-content").forEach(c => c.classList.remove("active"));
            btn.classList.add("active");
            const tabId = btn.getAttribute("data-tab");
            document.getElementById(`tab-${tabId}`).classList.add("active");
            if (tabId === "mylist") {
                renderWatchlist();
                renderHistory();
                renderDownloads();
            }
        });
    });

    /* Search */
    document.getElementById("searchBtn").addEventListener("click", () => {
        const q = document.getElementById("searchInput").value.trim();
        if (q) doSearch(q);
    });
    document.getElementById("searchInput").addEventListener("keypress", (e) => {
        if (e.key === "Enter") {
            const q = e.target.value.trim();
            if (q) doSearch(q);
        }
    });

    loadTrending();
    loadContinueWatching();
});

async function loadTrending() {
    const query = `
      query ($perPage: Int) {
        Page(perPage: $perPage) {
          trending(type: ANIME) {
            id
            title { romaji english }
            coverImage { large }
          }
        }
      }
    `;
    try {
        const data = await anilistQuery(query, { perPage: 20 });
        const list = data.Page.trending.map(a => ({
            id: a.id,
            title: a.title.english || a.title.romaji,
            cover: a.coverImage.large,
        }));
        renderAnimeCards("animeGrid", list, resolveAndPlay);
    } catch (e) {
        console.error("Trending error:", e);
        renderAnimeCards("animeGrid", MOCK_ANIME, resolveAndPlay);
    }
}

async function doSearch(query) {
    const q = `
      query ($search: String) {
        Page(perPage: 20) {
          media(search: $search, type: ANIME) {
            id
            title { romaji english }
            coverImage { large }
          }
        }
      }
    `;
    try {
        const data = await anilistQuery(q, { search: query });
        const list = data.Page.media.map(a => ({
            id: a.id,
            title: a.title.english || a.title.romaji,
            cover: a.coverImage.large,
        }));
        renderAnimeCards("animeGrid", list, resolveAndPlay);
    } catch (e) {
        console.error("Search error:", e);
        const filtered = MOCK_ANIME.filter(a => a.title.toLowerCase().includes(query.toLowerCase()));
        renderAnimeCards("animeGrid", filtered, resolveAndPlay);
    }
}

async function loadContinueWatching() {
    const list = getStorage(STORAGE_HISTORY);
    if (list.length === 0) {
        document.getElementById("continueGrid").innerHTML = "";
        return;
    }
    const historyCards = list.map(h => ({ id: h.id || h.title, title: h.title, cover: h.cover }));
    renderAnimeCards("continueGrid", historyCards, resolveAndPlay);
}
