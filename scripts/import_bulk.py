"""
快速批量导入：LOAD DATA LOCAL INFILE
用法: python3 scripts/import_bulk.py [--hot-rooms N] [--skew-ratio R] [--limit N]
"""
import argparse, glob, hashlib, os, sys, time, uuid
from datetime import datetime

import numpy as np
import pandas as pd
import pymysql

DB_CONFIG = {
    "host": "127.0.0.1", "port": 3307, "user": "root",
    "password": "root", "database": "danmakulive", "charset": "utf8mb4",
    "local_infile": True,
}

DATA_DIR = "/tmp"
CHANNELS_CSV = "/tmp/channels.csv"
CSV_FILE = "/tmp/import_bulk.csv"

def mkid(salt): return hashlib.sha1(salt.encode()).hexdigest()

def load_csv(conn, table, columns, csv_path):
    """LOAD DATA LOCAL INFILE"""
    sql = f"""LOAD DATA LOCAL INFILE '{csv_path}'
              INTO TABLE {table}
              CHARACTER SET utf8mb4
              FIELDS TERMINATED BY '\t'
              LINES TERMINATED BY '\n'
              ({', '.join(columns)})"""
    with conn.cursor() as cur:
        cur.execute(sql)
    conn.commit()

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--hot-rooms", type=int, default=5)
    parser.add_argument("--skew-ratio", type=float, default=0.6)
    parser.add_argument("--limit", type=int, default=0)
    parser.add_argument("--reset", action="store_true")
    args = parser.parse_args()

    conn = pymysql.connect(**DB_CONFIG)

    # reset
    if args.reset:
        print("Reset tables...")
        with conn.cursor() as cur:
            for t in ["video_danmaku", "live_danmaku", "video", "live_room", "user"]:
                cur.execute(f"DELETE FROM {t}")
        conn.commit()

    # channels
    channels_df = pd.read_csv(CHANNELS_CSV).head(300)
    channel_ids = channels_df["channelId"].tolist()
    hot_channel_ids = channel_ids[:args.hot_rooms]
    cold_channel_ids = channel_ids[args.hot_rooms:]
    all_channels = hot_channel_ids + cold_channel_ids

    hot_w = args.skew_ratio / args.hot_rooms if args.hot_rooms > 0 else 0
    cold_w = (1 - args.skew_ratio) / len(cold_channel_ids) if cold_channel_ids else 0
    weights = [hot_w] * args.hot_rooms + [cold_w] * len(cold_channel_ids)
    rng = np.random.default_rng(42)
    base_ts = int(datetime(2021, 1, 15, 20, 0, 0).timestamp() * 1000)

    # import rooms
    if conn.cursor().execute("SELECT COUNT(*) FROM live_room") == 0:
        print("Importing live_rooms...")
        import bcrypt
        rows = []
        for i, (_, ch) in enumerate(channels_df.iterrows()):
            uid = mkid(f"owner_{i}")
            pw = bcrypt.hashpw("123456".encode(), bcrypt.gensalt()).decode()
            # user
            conn.cursor().execute(
                "INSERT IGNORE INTO user (id,email,password,nickname,avatar_url,status,create_time,update_time) VALUES (%s,%s,%s,%s,%s,0,NOW(),NOW())",
                (uid, f"{uid[:8]}@mock.live", pw, uid[:8], ""))
            eng = ch.get("englishName", "")
            title = str(ch.get("name", ch["channelId"][:8]))
            if eng and str(eng) != "nan":
                title = f"{title} / {eng}"
            rows.append((ch["channelId"], title, uid))
        conn.commit()
        # batch insert rooms
        with conn.cursor() as cur:
            cur.executemany(
                "INSERT IGNORE INTO live_room (id,title,owner_id,status,replay_status,started_at,ended_at,create_time,update_time) VALUES (%s,%s,%s,2,2,'2021-01-14 20:00:00','2021-01-15 00:00:00',NOW(),NOW())",
                rows)
        conn.commit()
        print(f"  rooms: {len(rows)}")

    # import danmaku
    files = sorted(glob.glob(os.path.join(DATA_DIR, "chats_flagged_*.parquet")))
    files += sorted(glob.glob(os.path.join(DATA_DIR, "chats_nonflag_*.parquet")))

    total = 0
    now_str = datetime.now().strftime('%Y-%m-%d %H:%M:%S')

    for fi, f in enumerate(files):
        try:
            df = pd.read_parquet(f)
        except Exception as e:
            print(f"skip {os.path.basename(f)}: {e}")
            continue
        if "body" not in df.columns:
            continue

        df = df.dropna(subset=["body"])
        df = df[df["body"].str.len() > 0]
        n = len(df)
        if n == 0:
            continue

        # 向量化
        room_idx = rng.choice(len(all_channels), size=n, p=weights)
        df["room_id"] = [all_channels[i] for i in room_idx]
        df["user_id"] = df["body"].apply(lambda x: mkid(x.strip()))
        df["user_name"] = df["user_id"].str[:8]
        df["content"] = df["body"].str[:255]
        df["send_time"] = base_ts + rng.integers(0, 7200, size=n) * 1000

        # dm_id: 批量生成 (outside pandas for speed)
        dm_ids = [str(uuid.uuid4()).replace("-", "") for _ in range(n)]

        # 写入 CSV (TAB 分隔，避免逗号转义问题)
        with open(CSV_FILE, "w", encoding="utf-8") as fout:
            for i in range(n):
                fout.write(f"{dm_ids[i]}\t{df['room_id'].iloc[i]}\t"
                          f"{df['user_id'].iloc[i]}\t{df['user_name'].iloc[i]}\t"
                          f"{df['content'].iloc[i]}\t{int(df['send_time'].iloc[i])}\t"
                          f"{now_str}\t{now_str}\n")

        # LOAD DATA
        load_csv(conn, "live_danmaku",
                 ["id", "room_id", "user_id", "user_name", "content",
                  "send_time", "create_time", "update_time"],
                 CSV_FILE)

        total += n
        print(f"[{fi+1}/{len(files)}] {os.path.basename(f)}: {n} rows (total: {total})")

        if args.limit and total >= args.limit:
            break

    os.remove(CSV_FILE) if os.path.exists(CSV_FILE) else None

    # summary
    with conn.cursor() as cur:
        cur.execute("SELECT COUNT(*) FROM user")
        nu = cur.fetchone()[0]
        cur.execute("SELECT COUNT(*) FROM live_room")
        nr = cur.fetchone()[0]
        cur.execute("SELECT COUNT(*) FROM live_danmaku")
        nd = cur.fetchone()[0]
        cur.execute("SELECT room_id, COUNT(*) as cnt FROM live_danmaku GROUP BY 1 ORDER BY cnt DESC LIMIT 10")
        top = cur.fetchall()

    print(f"\nDone: user={nu}, rooms={nr}, danmaku={nd}")
    print("Top 10 rooms:")
    for rid, cnt in top:
        print(f"  {rid}: {cnt}")

    conn.close()

if __name__ == "__main__":
    main()
