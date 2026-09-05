# ききがき (KikiGaki)

日本語特化・完全オンデバイス文字起こし Android アプリ (Xiaomi 15T Pro 対象)

- リアルタイム仮テキスト(ストリーミングSTT) + Whisper 確定テキスト
- 話者分離(後処理)
- バックグラウンド録音 / HyperOS 対応

## ビルド

```bash
git push   # GitHub Actions がビルド
./scripts/install-apk.sh   # 最新 APK を実機へインストール
```

## フェーズ

- Phase 0: 環境整備(本スケルトン)
- Phase 1: 録音基盤
- Phase 2: ライブ文字起こし
- Phase 3: Whisper 確定
- Phase 4: 保存・出力
- Phase 5: 話者分離
- Phase 6: HyperOS チューニング
