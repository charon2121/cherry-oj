// Package install 接收控制面下发的测试数据并原子落盘。
// 它持有节点数据根的独占锁：同一目录不能被两个节点同时安装。
package install
