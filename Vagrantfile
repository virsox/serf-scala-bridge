# -*- mode: ruby -*-
# vi: set ft=ruby :
$script = <<SCRIPT

echo Installing depedencies...
sudo apt-get install -y unzip

echo Fetching Serf...
cd /tmp/
wget https://releases.hashicorp.com/serf/0.7.0/serf_0.7.0_linux_amd64.zip -O serf.zip

echo Installing Serf...
unzip serf.zip
sudo chmod +x serf
sudo mv serf /usr/bin/serf

SCRIPT

# Vagrantfile API/syntax version. Don't touch unless you know what you're doing!
VAGRANTFILE_API_VERSION = "2"

Vagrant.configure(VAGRANTFILE_API_VERSION) do |config|
  config.vm.box = "ubuntu/xenial64"

  config.vm.provision "shell", inline: $script

  config.vm.define "n1" do |n1|
      n1.vm.network "private_network", ip: "172.20.20.10"
  end

  config.vm.define "n2" do |n2|
      n2.vm.network "private_network", ip: "172.20.20.11"
  end

  config.vm.define "n3" do |n3|
      n3.vm.network "private_network", ip: "172.20.20.12"
  end
end
