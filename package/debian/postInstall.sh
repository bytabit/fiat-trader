#!/bin/sh -e

ec() {
    echo "$@" >&2
    "$@"
}

case "$1" in
    configure)
        # add user and group (if they don't already exist)
        adduser --system --group --quiet bytabit

        # cleanup permissions
        chown bytabit:bytabit -R /opt/bytabit
        chmod ug+r /opt/bytabit/fiat-trader/server/default.conf
        chmod u+w /opt/bytabit/fiat-trader/server/default.conf
        chmod u+rwx /opt/bytabit/fiat-trader/server/run.sh

        ;;
esac
