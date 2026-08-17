#!/system/bin/sh

MODDIR=${0%/*}
sleep 10
LOG=$MODDIR/logs/block.log
mkdir -p $MODDIR/logs
chmod 755 $MODDIR/bin/* 2>/dev/null

echo "[AdBlock] Started" >> $LOG

$MODDIR/bin/dnsfilter $MODDIR/config/blacklist.txt $LOG &
$MODDIR/bin/netwatch $LOG &
$MODDIR/bin/ruleengine $MODDIR/config/signature.txt $MODDIR/config/auto_blacklist.txt &

exit 0
