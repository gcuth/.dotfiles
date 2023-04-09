return {
  { "tpope/vim-commentary", lazy = false },
  { "tpope/vim-unimpaired", lazy = false },
  { "tpope/vim-surround",   lazy = false },
  {
    "tpope/vim-fireplace",
    ft = { "clojure", "clojurescript" }
  },
  {
    "kien/rainbow_parentheses.vim",
    ft = { "clojure", "clojurescript" }
  },
  --
  -- You can also add new plugins here as well:
  -- Add plugins, the lazy syntax
  -- "andweeb/presence.nvim",
  -- {
  --   "ray-x/lsp_signature.nvim",
  --   event = "BufRead",
  --   config = function()
  --     require("lsp_signature").setup()
  --   end,
  -- },
}
