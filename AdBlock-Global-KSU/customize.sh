#!/system/bin/sh

ui_print "=========================="
ui_print " AdBlock Global Installer "
ui_print "=========================="

MODPATH=${0%/*}

mkdir -p $MODPATH/bin
mkdir -p $MODPATH/config
mkdir -p $MODPATH/logs
mkdir -p $MODPATH/webroot

touch $MODPATH/logs/block.log
chmod -R 755 $MODPATH/bin

ui_print "Installation completed"
