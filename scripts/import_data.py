"""
数据导入脚本：
  sensai (Kaggle)      → live_room + live_danmaku + user
  DanmakuTPPBench (HF) → video + video_danmaku (需 HF 登录)

用法:
  python3 scripts/import_data.py [--skip-live] [--skip-video] [--limit N]

数据来源:
  - sensai: Kaggle uetchy/sensai, ~700万条直播聊天, body 字段
  - channels.csv: VTuber 1B Elements, 1359 条频道元数据
  - DanmakuTPPBench: HuggingFace FRENKIE-CHIANG/DanmakuTPP (需登录)
"""

import argparse
import hashlib
import os
import sys
import time
import uuid
from datetime import datetime, timedelta
from typing import Iterator

import bcrypt
import pandas as pd
import pymysql

# ---------- config ----------

DB_CONFIG = {
    "host": "127.0.0.1",
    "port": 3307,
    "user": "root",
    "password": "root",
    "database": "danmakulive",
    "charset": "utf8mb4",
}

BATCH_SIZE = 2000

# ---------- helpers ----------

def mkid(salt: str) -> str:
    """用 SHA-1 生成 40 位 hex user_id (和 sensai 原始格式一致)"""
    return hashlib.sha1(salt.encode()).hexdigest()


def bcrypt_pw(password: str) -> str:
    return bcrypt.hashpw(password.encode(), bcrypt.gensalt()).decode()


# ---------- importer ----------

class Importer:
    def __init__(self, conn):
        self.conn = conn
        self.cursor = conn.cursor()
        self.stats = {}

    def count(self, table: str) -> int:
        self.cursor.execute(f"SELECT COUNT(*) FROM {table}")
        return self.cursor.fetchone()[0]

    def batch_insert(self, table: str, columns: list, rows: list) -> int:
        if not rows:
            return 0
        cols = ", ".join(columns)
        ph = ", ".join(["%s"] * len(columns))
        self.cursor.executemany(f"INSERT IGNORE INTO {table} ({cols}) VALUES ({ph})", rows)
        self.conn.commit()
        return self.cursor.rowcount

    def import_users(self, user_ids: list, batch_name: str = "user") -> int:
        existing = self.count("user")
        if existing > len(user_ids) * 0.9:
            print(f"  skip: user 已有 {existing} 条")
            return 0

        rows = []
        for uid in user_ids:
            nick = uid[:8]
            rows.append((uid, f"{nick}@mock.live", bcrypt_pw("123456"),
                         nick, "", 0, datetime.now(), datetime.now()))

        cols = ["id", "email", "password", "nickname", "avatar_url",
                "status", "create_time", "update_time"]
        n = 0
        for i in range(0, len(rows), BATCH_SIZE):
            n += self.batch_insert("user", cols, rows[i:i+BATCH_SIZE])
            print(f"  {batch_name}: {n}/{len(rows)}", end="\r")
        print(f"  {batch_name}: {n} done")
        self.stats["user"] = n
        return n

    def import_live_rooms(self, channels_df) -> int:
        if self.count("live_room") > 10:
            print("  skip: live_room 已有数据")
            return 0

        ch_df = channels_df.head(300)
        user_ids = [mkid(f"owner_{i}") for i in range(len(ch_df))]
        # 先插入房主
        self.import_users(user_ids, "room_owners")

        rows = []
        for i, (_, ch) in enumerate(ch_df.iterrows()):
            rid = ch["channelId"]
            eng = ch.get("englishName", "")
            title = str(ch.get("name", rid[:8]))
            if eng and str(eng) != "nan":
                title = f"{title} / {eng}"
            rows.append((rid, title, user_ids[i], 2, None, 2,
                        datetime(2021, 1, 14, 20, 0, 0),
                        datetime(2021, 1, 15, 0, 0, 0),
                        datetime.now(), datetime.now()))

        cols = ["id", "title", "owner_id", "status", "replay_video_id",
                "replay_status", "started_at", "ended_at",
                "create_time", "update_time"]
        n = self.batch_insert("live_room", cols, rows)
        print(f"  live_room: {n} done")
        self.stats["live_room"] = n
        return n

    def import_live_danmaku(self, rows: list, max_rows: int = 0) -> int:
        if max_rows and max_rows < len(rows):
            rows = rows[:max_rows]

        cols = ["id", "room_id", "user_id", "user_name", "content",
                "send_time", "create_time", "update_time"]
        total = 0
        now = datetime.now()

        for i in range(0, len(rows), BATCH_SIZE):
            batch = []
            for row in rows[i:i+BATCH_SIZE]:
                room_id, user_id, user_name, content, send_time = row
                dm_id = str(uuid.uuid4()).replace("-", "")
                batch.append((dm_id, room_id, user_id, user_name, content[:255],
                             send_time, now, now))
            total += self.batch_insert("live_danmaku", cols, batch)
            print(f"  live_danmaku: {total}/{len(rows)}", end="\r")

        print(f"  live_danmaku: {total} done")
        self.stats["live_danmaku"] = total
        return total

    def import_videos(self, video_ids: list) -> int:
        if self.count("video") > 10:
            print("  skip: video 已有数据")
            return 0

        import random
        rows = []
        for vid in video_ids:
            rows.append((vid, f"Video_{vid[:8]}", random.randint(60, 7200),
                        None, None, datetime.now(), datetime.now()))

        cols = ["id", "title", "duration", "owner_id", "object_key",
                "create_time", "update_time"]
        n = self.batch_insert("video", cols, rows)
        print(f"  video: {n} done")
        self.stats["video"] = n
        return n

    def import_video_danmaku(self, gen: Iterator[tuple], max_rows: int) -> int:
        if self.count("video_danmaku") > 1000:
            print("  skip: video_danmaku 已有数据")
            return 0

        cols = ["id", "video_id", "user_id", "user_name", "content",
                "playback_time", "send_time", "create_time", "update_time"]
        total = 0
        batch = []
        now = datetime.now()

        for row in gen:
            batch.append(row + (now, now))
            if len(batch) >= BATCH_SIZE:
                total += self.batch_insert("video_danmaku", cols, batch)
                batch = []
                print(f"  video_danmaku: {total}", end="\r")
            if max_rows and total >= max_rows:
                break

        if batch:
            total += self.batch_insert("video_danmaku", cols, batch)
        print(f"  video_danmaku: {total} done")
        self.stats["video_danmaku"] = total
        return total


# ---------- data loaders ----------

def load_sensai(data_dir: str, channels_df, hot_rooms: int = 5, skew_ratio: float = 0.6,
                chunk_size: int = 500000) -> tuple:
    """分块加载 sensai 数据并写入 DB，返回 unique_users

    避免一次性加载 700 万行到内存
    """
    import glob
    import numpy as np

    print(f"\n=== sensai: 直播弹幕 (hot_rooms={hot_rooms}, skew_ratio={skew_ratio}) ===")

    files = sorted(glob.glob(os.path.join(data_dir, "chats_flagged_*.parquet")))
    files += sorted(glob.glob(os.path.join(data_dir, "chats_nonflag_*.parquet")))

    if not files:
        raise FileNotFoundError(f"No sensai parquet files in {data_dir}")

    print(f"  loading {len(files)} files for schema...")
    # 先读第一个文件获取 schema
    sample = pd.read_parquet(files[0])
    print(f"  columns: {list(sample.columns)}")

    channel_ids = channels_df.head(300)["channelId"].tolist()
    hot_channel_ids = channel_ids[:hot_rooms]
    cold_channel_ids = channel_ids[hot_rooms:]
    all_channels = hot_channel_ids + cold_channel_ids

    hot_weight = skew_ratio / hot_rooms if hot_rooms > 0 else 0
    cold_weight = (1 - skew_ratio) / len(cold_channel_ids) if cold_channel_ids else 0
    weights = [hot_weight] * hot_rooms + [cold_weight] * len(cold_channel_ids)
    print(f"  hot weight per room: {hot_weight:.4f}, cold weight: {cold_weight:.4f}")

    rng = np.random.default_rng(42)
    base_ts = int(datetime(2021, 1, 15, 20, 0, 0).timestamp() * 1000)
    all_users = set()
    total_rows = 0

    cols = ["id", "room_id", "user_id", "user_name", "content",
            "send_time", "create_time", "update_time"]
    now = datetime.now()

    # 分文件读取，逐文件处理
    for fi, f in enumerate(files):
        try:
            df = pd.read_parquet(f)
        except Exception as e:
            print(f"  skip {os.path.basename(f)}: {e}")
            continue

        if "body" not in df.columns:
            continue

        df = df.dropna(subset=["body"])
        df = df[df["body"].str.len() > 0]
        if len(df) == 0:
            continue

        # 向量化赋值
        n = len(df)
        room_indices = rng.choice(len(all_channels), size=n, p=weights)
        df["room_id"] = [all_channels[i] for i in room_indices]
        df["user_id"] = df["body"].apply(lambda x: mkid(x.strip()))
        df["user_name"] = df["user_id"].str[:8]
        df["content"] = df["body"].str[:255]
        df["send_time"] = base_ts + rng.integers(0, 7200, size=n) * 1000

        all_users.update(df["user_id"].unique())

        # 转成 rows 并按 chunk_size 分批返回
        room_ids = df["room_id"].tolist()
        user_ids = df["user_id"].tolist()
        user_names = df["user_name"].tolist()
        contents = df["content"].tolist()
        send_times = df["send_time"].astype(int).tolist()

        for start in range(0, n, chunk_size):
            end = min(start + chunk_size, n)
            chunk = list(zip(
                room_ids[start:end],
                user_ids[start:end],
                user_names[start:end],
                contents[start:end],
                send_times[start:end]
            ))
            total_rows += len(chunk)
            yield chunk  # generator 逐个产出 chunk
            del chunk  # 释放内存

        print(f"  [{fi+1}/{len(files)}] {os.path.basename(f)}: {n} rows, total: {total_rows}")
        del df  # 释放每文件的内存

    print(f"  total rows: {total_rows}, unique users: {len(all_users)}")
    yield None  # sentinel: 结束
    global _sensai_users
    _sensai_users = all_users


def generate_video_from_live(conn, imp, max_videos: int, max_danmaku: int) -> tuple:
    """从 live_danmaku 数据生成 video + video_danmaku"""
    import random

    print("\n=== generate-video: 从 live_danmaku 生成 ===")

    cursor = conn.cursor()

    # 获取现有 user_id（房主）
    cursor.execute("SELECT id FROM user LIMIT 300")
    owner_ids = [r[0] for r in cursor.fetchall()]
    if not owner_ids:
        print("  ⚠ user 表为空，请先 --skip-video 导入直播数据")
        return

    # 创建 video 记录
    video_ids = []
    for i in range(max_videos):
        vid = str(uuid.uuid4()).replace("-", "")
        video_ids.append(vid)

    import random
    rows = []
    for vid in video_ids:
        owner = random.choice(owner_ids)
        rows.append((vid, f"Video_{vid[:8]}", random.randint(60, 7200),
                    owner, None, datetime.now(), datetime.now()))

    cols = ["id", "title", "duration", "owner_id", "object_key",
            "create_time", "update_time"]
    n = imp.batch_insert("video", cols, rows)
    print(f"  video: {n} done")

    # 从 live_danmaku 取数据转成 video_danmaku
    batch_size = 500
    offset = 0
    total = 0
    cols = ["id", "video_id", "user_id", "user_name", "content",
            "playback_time", "send_time", "create_time", "update_time"]
    now = datetime.now()
    base_send_time = int(datetime(2021, 1, 15, 20, 0, 0).timestamp() * 1000)

    while total < max_danmaku:
        cursor.execute(
            "SELECT user_id, user_name, content, send_time FROM live_danmaku "
            "LIMIT %s OFFSET %s", (batch_size, offset))
        rows = cursor.fetchall()
        if not rows:
            break

        batch = []
        for user_id, user_name, content, send_time in rows:
            dm_id = str(uuid.uuid4()).replace("-", "")
            vid = random.choice(video_ids)
            # 视频时长最大 7200s，弹幕均匀分布
            playback_time = round(random.uniform(0, 7200), 2)
            batch.append((dm_id, vid, user_id, user_name, content[:255],
                         playback_time, send_time, now, now))

        total += imp.batch_insert("video_danmaku", cols, batch)
        print(f"  video_danmaku: {total}", end="\r")
        offset += batch_size

    print(f"  video_danmaku: {total} done")
    imp.stats["video"] = n
    imp.stats["video_danmaku"] = total


# ---------- main ----------

def main():
    parser = argparse.ArgumentParser(description="DanmakuLive 数据导入")
    parser.add_argument("--skip-live", action="store_true", help="跳过直播数据")
    parser.add_argument("--generate-video", action="store_true",
                        help="从 live_danmaku 生成视频弹幕数据（无需外部数据集）")
    parser.add_argument("--reset", action="store_true", help="清空所有表后重新导入")
    parser.add_argument("--limit", type=int, default=0, help="弹幕导入上限 (0=全部)")
    parser.add_argument("--max-videos", type=int, default=100, help="生成视频数")
    parser.add_argument("--hot-rooms", type=int, default=5, help="热点房间数")
    parser.add_argument("--skew-ratio", type=float, default=0.6, help="热点房间弹幕占比")
    parser.add_argument("--data-dir", type=str, default="/tmp", help="sensai parquet 目录")
    parser.add_argument("--channels", type=str, default="/tmp/channels.csv", help="channels.csv 路径")
    args = parser.parse_args()

    print("=" * 60)
    print("DanmakuLive 数据导入")
    print("=" * 60)

    # 加载频道元数据
    if not os.path.exists(args.channels):
        print(f"  ⚠ channels.csv not found at {args.channels}")
        print("  Download: kaggle datasets download uetchy/vtuber-livechat-elements -f channels.csv")
        sys.exit(1)
    channels_df = pd.read_csv(args.channels)
    print(f"channels: {len(channels_df)}")

    conn = pymysql.connect(**DB_CONFIG)
    imp = Importer(conn)

    if args.reset:
        print("\n>>> 清空所有表...")
        with conn.cursor() as cur:
            cur.execute("DELETE FROM video_danmaku")
            cur.execute("DELETE FROM live_danmaku")
            cur.execute("DELETE FROM video")
            cur.execute("DELETE FROM live_room")
            cur.execute("DELETE FROM user")
            conn.commit()
        print("  done")

    try:
        # ---- live data ----
        if not args.skip_live:
            imp.import_live_rooms(channels_df)
            for chunk in load_sensai(
                args.data_dir, channels_df,
                hot_rooms=args.hot_rooms,
                skew_ratio=args.skew_ratio):
                if chunk is None:
                    break
                imp.import_live_danmaku(chunk, max_rows=0)  # chunk 内部已全量

        # ---- video data ----
        if args.generate_video:
            generate_video_from_live(conn, imp,
                                     max_videos=args.max_videos,
                                     max_danmaku=args.limit)

        # ---- summary ----
        print("\n" + "=" * 60)
        print("导入完成!")
        print("=" * 60)
        for t, c in sorted(imp.stats.items()):
            print(f"  {t}: {c}")
        print()

    finally:
        conn.close()


if __name__ == "__main__":
    main()
