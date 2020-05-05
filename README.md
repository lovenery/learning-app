# MAC Learning App

> ONOS App - MAC Learning

## Environment

- OpenJDK: 11.0.7
- ONOS: 2.2.2

## Test

```bash
# h1 --- s1 --- h2
sudo mn --controller=remote,127.0.0.1,6653

#        h2            h3
#        |             |
# h1 --- s2 --- s1 --- s3 --- h4
sudo mn --controller=remote,127.0.0.1,6653 --topo=tree,depth=2

# Test
#
cd ~/code/mininet/util
./m h1
./m h2
#
mininet> h1 ip n flush dev h1-eth0
mininet> h2 ip n flush dev h2-eth0
mininet> h1 arping -c 1 h2
mininet> sh ovs-ofctl dump-flows s1
#
arping 10.0.0.2 -c 1
ip n flush dev h1-eth0
```