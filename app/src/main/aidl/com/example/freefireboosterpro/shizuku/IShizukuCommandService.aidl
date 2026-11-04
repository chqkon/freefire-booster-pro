package com.example.freefireboosterpro.shizuku;

interface IShizukuCommandService {
    void destroy() = 16777114;
    String execute(in String[] command) = 1;
}
