#!/system/bin/sh
# GPIO scan script for RK3588 / RK3855
# This will loop through GPIOs 0–200, try to export, set as output, and toggle HIGH/LOW.

START=0
END=200

LOGFILE=/sdcard/gpio_scan.log
echo "Starting GPIO scan... Results will be in $LOGFILE" > $LOGFILE

for i in $(seq $START $END); do
    GPIO_PATH=/sys/class/gpio/gpio$i

    # Export if not already exported
    if [ ! -d "$GPIO_PATH" ]; then
        echo $i > /sys/class/gpio/export 2>/dev/null
        sleep 0.1
    fi

    if [ -d "$GPIO_PATH" ]; then
        echo "Testing GPIO $i" | tee -a $LOGFILE

        # Try setting direction
        echo out > $GPIO_PATH/direction 2>/dev/null

        # Toggle HIGH
        echo 1 > $GPIO_PATH/value
        sleep 0.2

        # Toggle LOW
        echo 0 > $GPIO_PATH/value
        sleep 0.2

        echo "GPIO $i toggled HIGH->LOW" | tee -a $LOGFILE
    else
        echo "GPIO $i not available" | tee -a $LOGFILE
    fi
done

echo "Scan finished. Check with a multimeter or LED for activity."
echo "Full log saved at $LOGFILE"
